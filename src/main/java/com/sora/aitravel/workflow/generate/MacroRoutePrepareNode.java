package com.sora.aitravel.workflow.generate;

import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.CANDIDATE_POOL;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.DAY_SKELETONS;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.REQUIREMENT;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.SELECTED_QUOTE;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.config.AiGateway;
import com.sora.aitravel.dto.model.RentalQuoteOptionDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.model.poi.Poi;
import com.sora.aitravel.model.AreaAnchorCandidate;
import com.sora.aitravel.model.AreaAnchorSnapshot;
import com.sora.aitravel.model.CandidatePool;
import com.sora.aitravel.model.DaySkeleton;
import com.sora.aitravel.service.AmapPoiCacheService;
import com.sora.aitravel.service.route.GeoRouteCalculator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Builds the route skeleton from normalized city/area anchors before any spot filling happens. */
@Slf4j
@Component
@RequiredArgsConstructor
public class MacroRoutePrepareNode {

    private static final String POI_SHOW_FIELDS = "business,navi,photos";
    private static final String AI_MACRO_ROUTE_PROMPT =
            """
            规划 %s %d 天游的每日主锚点。偏好=%s；到达/取车=%s；租车=%s。
            规则：每天 1 个 mainPlace，2-4 个 anchorPlaces；都必须是真实可搜景区/街区/公园/博物馆/古镇/片区；不要泛词和附属设施；Day1 顺路轻量，后续按方向推进不折返。只返回 JSON：
            {
              "days": [
                {
                  "day": 1,
                  "city": "成都",
                  "theme": "市区轻量游",
                  "direction": "从到达点向西北推进",
                  "mainPlace": "宽窄巷子",
                  "anchorPlaces": ["宽窄巷子","人民公园","杜甫草堂"]
                }
              ]
            }
            """;

    private final AiGateway aiGateway;
    private final ObjectMapper objectMapper;
    private final AmapPoiCacheService amapPoiCacheService;

    public Map<String, Object> execute(OverAllState state) {
        CandidatePool pool =
                TripGraphStateCodec.required(state, CANDIDATE_POOL, CandidatePool.class);
        TravelRequirementDTO requirement =
                TripGraphStateCodec.required(state, REQUIREMENT, TravelRequirementDTO.class);
        RentalQuoteOptionDTO selectedQuote =
                TripGraphStateCodec.optional(state, SELECTED_QUOTE, RentalQuoteOptionDTO.class)
                        .orElse(null);
        List<DaySkeleton> skeletons = buildSkeletons(pool, requirement, selectedQuote);
        return TripGraphStateCodec.patch(DAY_SKELETONS, skeletons);
    }

    private List<DaySkeleton> buildSkeletons(
            CandidatePool pool,
            TravelRequirementDTO requirement,
            RentalQuoteOptionDTO selectedQuote) {
        try {
            List<DaySkeleton> aiSkeletons = buildAiSkeletons(pool, requirement, selectedQuote);
            if (!aiSkeletons.isEmpty()) {
                log.info(
                        "节点[macro-route-prepare]：使用 AI 路线骨架，days={}",
                        aiSkeletons.stream()
                                .map(
                                        item ->
                                                "D"
                                                        + item.getDay()
                                                        + "="
                                                        + item.getFocusArea().getCity()
                                                        + "/"
                                                        + item.getFocusArea().getName())
                                .toList());
                return aiSkeletons;
            }
        } catch (Exception exception) {
            log.warn("节点[macro-route-prepare]：AI 路线骨架失败，降级数据库骨架，reason={}", exception.getMessage());
        }
        return buildDatabaseSkeletons(pool, requirement, selectedQuote);
    }

    private List<DaySkeleton> buildAiSkeletons(
            CandidatePool pool,
            TravelRequirementDTO requirement,
            RentalQuoteOptionDTO selectedQuote)
            throws Exception {
        int days = requireDays(requirement);
        String destination = displayDestination(requirement, pool);
        AreaAnchorCandidate tripOrigin =
                selectedQuote != null && pool != null && pool.getPickupAnchor() != null
                        ? pool.getPickupAnchor()
                        : null;
        String prompt =
                AI_MACRO_ROUTE_PROMPT.formatted(
                        destination,
                        days,
                        preferenceText(requirement),
                        tripOrigin == null
                                ? "未明确"
                                : firstNonBlank(tripOrigin.getName(), tripOrigin.getAddress()),
                        selectedQuote == null
                                ? "未选择租车"
                                : firstNonBlank(
                                        selectedQuote.getDisplayName(),
                                        selectedQuote.getGroupName()));
        String json = aiGateway.callJsonObject("AI 路线骨架", prompt);
        JsonNode dayNodes = objectMapper.readTree(json).path("days");
        if (!dayNodes.isArray() || dayNodes.size() < days) {
            return List.of();
        }
        List<DaySkeleton> skeletons = new ArrayList<>();
        Set<String> usedAreaIds = new LinkedHashSet<>();
        AreaAnchorCandidate previousStay = null;
        for (int index = 0; index < days; index++) {
            JsonNode node = dayNodes.get(index);
            int dayNo = node.path("day").asInt(index + 1);
            String city = firstNonBlank(text(node, "city"), destination);
            AreaAnchorCandidate focus =
                    resolveAiAnchor(dayNo, city, text(node, "mainPlace"), usedAreaIds);
            if (focus == null) {
                focus = resolveAnchorPlaces(dayNo, city, node.path("anchorPlaces"), usedAreaIds);
            }
            if (focus == null) {
                return List.of();
            }
            usedAreaIds.add(focus.getId());
            AreaAnchorCandidate start =
                    dayNo == 1 && tripOrigin != null
                            ? tripOrigin
                            : firstNonNull(previousStay, focus);
            DaySkeleton skeleton = new DaySkeleton();
            skeleton.setDay(dayNo);
            skeleton.setTheme(firstNonBlank(text(node, "theme"), focus.getName() + "主题游"));
            skeleton.setTargetArea(focus.getName());
            skeleton.setIntensity(firstNonBlank(requirement.getPace(), "NORMAL"));
            skeleton.setStartAreaId(start.getId());
            skeleton.setFocusAreaId(focus.getId());
            skeleton.setEndAreaId(focus.getId());
            skeleton.setStayAreaId(focus.getId());
            skeleton.setStartArea(snapshot(start));
            skeleton.setFocusArea(snapshot(focus));
            skeleton.setEndArea(snapshot(focus));
            skeleton.setStayArea(snapshot(focus));
            skeletons.add(skeleton);
            previousStay = focus;
        }
        validateAiSkeletons(skeletons, days);
        return skeletons;
    }

    private AreaAnchorCandidate resolveAnchorPlaces(
            int dayNo, String city, JsonNode places, Set<String> usedAreaIds) {
        if (!places.isArray()) {
            return null;
        }
        for (JsonNode place : places) {
            AreaAnchorCandidate anchor = resolveAiAnchor(dayNo, city, place.asText(), usedAreaIds);
            if (anchor != null) {
                return anchor;
            }
        }
        return null;
    }

    private AreaAnchorCandidate resolveAiAnchor(
            int dayNo, String city, String place, Set<String> usedAreaIds) {
        String cleanPlace = cleanPlaceName(place);
        if (cleanPlace.isBlank() || isBadAnchorName(cleanPlace)) {
            return null;
        }
        List<Poi> pois =
                amapPoiCacheService.searchText(
                        city + " " + cleanPlace,
                        null,
                        city,
                        true,
                        10,
                        1,
                        POI_SHOW_FIELDS,
                        "AI_ROUTE_ANCHOR");
        for (Poi poi : pois) {
            if (poi.getId() == null
                    || poi.getName() == null
                    || poi.getLocation() == null
                    || isBadAnchorName(poi.getName())
                    || !matchesPlaceName(cleanPlace, poi.getName())) {
                continue;
            }
            String id = "AI_ROUTE_D" + dayNo + "_" + poi.getId();
            if (usedAreaIds.contains(id)) {
                continue;
            }
            return new AreaAnchorCandidate(
                    id,
                    poi.getName(),
                    "SCENIC_CLUSTER",
                    firstNonBlank(poi.getCityname(), city),
                    firstNonBlank(poi.getAdname(), poi.getCityname()),
                    poi.getAddress(),
                    poi.getLocation(),
                    "AI_AMAP_ROUTE",
                    poi.getId(),
                    poi.getBusiness() == null ? List.of() : splitTags(poi.getBusiness().getTag()));
        }
        return null;
    }

    private void validateAiSkeletons(List<DaySkeleton> skeletons, int days) {
        if (skeletons.size() != days) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "AI 路线骨架天数不完整");
        }
        Set<String> used = new LinkedHashSet<>();
        for (int index = 0; index < skeletons.size(); index++) {
            DaySkeleton skeleton = skeletons.get(index);
            if (skeleton.getFocusArea() == null || skeleton.getFocusArea().getLocation() == null) {
                throw new BusinessException(
                        ErrorCode.AI_GENERATE_ERROR, "AI 路线骨架缺少可用坐标，day=" + skeleton.getDay());
            }
            if (!used.add(skeleton.getFocusAreaId())) {
                throw new BusinessException(
                        ErrorCode.AI_GENERATE_ERROR,
                        "AI 路线骨架重复使用片区：" + skeleton.getFocusArea().getName());
            }
            if (index > 0) {
                DaySkeleton previous = skeletons.get(index - 1);
                if (!previous.getStayAreaId().equals(skeleton.getStartAreaId())) {
                    throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "AI 路线骨架跨天衔接失败");
                }
            }
        }
    }

    private List<DaySkeleton> buildDatabaseSkeletons(
            CandidatePool pool,
            TravelRequirementDTO requirement,
            RentalQuoteOptionDTO selectedQuote) {
        int days = requireDays(requirement);
        List<AreaAnchorCandidate> scenicAreas = scenicAreas(pool);
        List<String> routeCities = routeCities(requirement, scenicAreas);
        Map<String, List<AreaAnchorCandidate>> areasByCity = areasByCity(routeCities, scenicAreas);
        List<String> dayCities = allocateDayCities(days, routeCities, areasByCity);

        log.info(
                "节点[macro-route-prepare]：按城市生成骨架，days={}, routeCities={}, dayCities={}, areaCounts={}",
                days,
                routeCities,
                dayCities,
                areaCounts(areasByCity));

        List<DaySkeleton> skeletons = new ArrayList<>();
        Map<String, Set<String>> usedAreaIdsByCity = new LinkedHashMap<>();
        Map<String, String> previousGroupByCity = new LinkedHashMap<>();
        AreaAnchorCandidate previousStay = null;
        AreaAnchorCandidate tripOrigin =
                selectedQuote != null && pool != null && pool.getPickupAnchor() != null
                        ? pool.getPickupAnchor()
                        : null;

        for (int index = 0; index < dayCities.size(); index++) {
            int dayNo = index + 1;
            String city = dayCities.get(index);
            AreaAnchorCandidate focus =
                    chooseFocusArea(
                            areasByCity.get(city),
                            usedAreaIdsByCity.computeIfAbsent(city, key -> new LinkedHashSet<>()),
                            previousGroupByCity.get(city),
                            tripOrigin,
                            previousStay);
            if (focus == null) {
                throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "城市缺少可用旅行片区：" + city);
            }
            usedAreaIdsByCity.get(city).add(focus.getId());
            previousGroupByCity.put(city, areaGroup(focus));

            AreaAnchorCandidate start =
                    dayNo == 1
                                    && selectedQuote != null
                                    && pool != null
                                    && pool.getPickupAnchor() != null
                            ? pool.getPickupAnchor()
                            : firstNonNull(previousStay, focus);
            DaySkeleton skeleton = new DaySkeleton();
            skeleton.setDay(dayNo);
            skeleton.setTheme(theme(focus, requirement));
            skeleton.setTargetArea(focus.getName());
            skeleton.setIntensity(firstNonBlank(requirement.getPace(), "NORMAL"));
            skeleton.setStartAreaId(start.getId());
            skeleton.setFocusAreaId(focus.getId());
            skeleton.setEndAreaId(focus.getId());
            skeleton.setStayAreaId(focus.getId());
            skeleton.setStartArea(snapshot(start));
            skeleton.setFocusArea(snapshot(focus));
            skeleton.setEndArea(snapshot(focus));
            skeleton.setStayArea(snapshot(focus));
            skeletons.add(skeleton);
            previousStay = focus;
        }

        validateSkeletons(skeletons, dayCities, days);
        log.info(
                "节点[macro-route-prepare]：产出 DaySkeletons，days={}",
                skeletons.stream()
                        .map(
                                item ->
                                        "D"
                                                + item.getDay()
                                                + "="
                                                + item.getFocusArea().getCity()
                                                + "/"
                                                + item.getFocusArea().getName())
                        .toList());
        return skeletons;
    }

    private int requireDays(TravelRequirementDTO requirement) {
        if (requirement.getDays() == null || requirement.getDays() < 1) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "路线骨架缺少有效天数");
        }
        return requirement.getDays();
    }

    private List<AreaAnchorCandidate> scenicAreas(CandidatePool pool) {
        if (pool == null || pool.getAreaAnchors() == null) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "缺少可用于路线骨架的区域候选");
        }
        List<AreaAnchorCandidate> scenicAreas =
                pool.getAreaAnchors().stream()
                        .filter(anchor -> "SCENIC_CLUSTER".equals(anchor.getRole()))
                        .filter(MacroRoutePrepareNode::usable)
                        .sorted(
                                Comparator.comparing(
                                                (AreaAnchorCandidate anchor) ->
                                                        !"TRAVEL_AREA".equals(anchor.getSource()))
                                        .thenComparing(AreaAnchorCandidate::getCity)
                                        .thenComparing(AreaAnchorCandidate::getName))
                        .toList();
        if (scenicAreas.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "缺少可用于路线骨架的旅行片区");
        }
        return scenicAreas;
    }

    private List<String> routeCities(
            TravelRequirementDTO requirement, List<AreaAnchorCandidate> scenicAreas) {
        LinkedHashSet<String> cities = new LinkedHashSet<>();
        if (requirement.getRouteCities() != null) {
            requirement.getRouteCities().stream()
                    .map(this::normalizeCity)
                    .filter(city -> !city.isBlank())
                    .forEach(cities::add);
        }
        if (cities.isEmpty()) {
            scenicAreas.stream()
                    .map(AreaAnchorCandidate::getCity)
                    .map(this::normalizeCity)
                    .filter(city -> !city.isBlank())
                    .forEach(cities::add);
        }
        if (cities.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "路线骨架缺少目标城市");
        }
        return new ArrayList<>(cities);
    }

    private Map<String, List<AreaAnchorCandidate>> areasByCity(
            List<String> routeCities, List<AreaAnchorCandidate> scenicAreas) {
        Map<String, List<AreaAnchorCandidate>> result = new LinkedHashMap<>();
        for (String city : routeCities) {
            String normalizedCity = normalizeCity(city);
            List<AreaAnchorCandidate> areas =
                    scenicAreas.stream()
                            .filter(area -> normalizeCity(area.getCity()).equals(normalizedCity))
                            .toList();
            if (!areas.isEmpty()) {
                result.put(normalizedCity, areas);
            }
        }
        if (result.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "目标城市没有可用旅行片区");
        }
        return result;
    }

    private List<String> allocateDayCities(
            int days,
            List<String> routeCities,
            Map<String, List<AreaAnchorCandidate>> areasByCity) {
        List<String> availableCities =
                routeCities.stream()
                        .map(this::normalizeCity)
                        .filter(areasByCity::containsKey)
                        .distinct()
                        .toList();
        if (availableCities.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "路线骨架没有可分配城市");
        }
        int cityCount = Math.min(days, availableCities.size());
        List<String> selectedCities = availableCities.stream().limit(cityCount).toList();
        Map<String, Integer> quota = new LinkedHashMap<>();
        selectedCities.forEach(city -> quota.put(city, 1));
        int remainingDays = days - selectedCities.size();
        while (remainingDays > 0) {
            String city =
                    selectedCities.stream()
                            .max(
                                    Comparator.comparingInt(
                                                    item ->
                                                            areasByCity.get(item).size()
                                                                    - quota.get(item))
                                            .thenComparingInt(item -> areasByCity.get(item).size()))
                            .orElse(selectedCities.get(0));
            quota.put(city, quota.get(city) + 1);
            remainingDays--;
        }
        List<String> dayCities = new ArrayList<>();
        for (String city : selectedCities) {
            for (int index = 0; index < quota.get(city); index++) {
                dayCities.add(city);
            }
        }
        return dayCities;
    }

    private AreaAnchorCandidate chooseFocusArea(
            List<AreaAnchorCandidate> areas,
            Set<String> usedAreaIds,
            String previousGroup,
            AreaAnchorCandidate tripOrigin,
            AreaAnchorCandidate previousStay) {
        if (areas == null || areas.isEmpty()) {
            return null;
        }
        List<AreaAnchorCandidate> unused =
                areas.stream().filter(area -> !usedAreaIds.contains(area.getId())).toList();
        if (unused.isEmpty()) {
            return areas.get(0);
        }
        List<AreaAnchorCandidate> progressive = progressiveAreas(unused, tripOrigin, previousStay);
        AreaAnchorCandidate selected =
                progressive.stream()
                        .filter(area -> !usedAreaIds.contains(area.getId()))
                        .filter(
                                area ->
                                        previousGroup == null
                                                || !previousGroup.equals(areaGroup(area)))
                        .findFirst()
                        .orElseGet(() -> progressive.stream().findFirst().orElse(unused.get(0)));
        return selected;
    }

    private List<AreaAnchorCandidate> progressiveAreas(
            List<AreaAnchorCandidate> areas,
            AreaAnchorCandidate tripOrigin,
            AreaAnchorCandidate previousStay) {
        if (areas.size() <= 1 || tripOrigin == null || previousStay == null) {
            return areas;
        }
        double[] origin = location(tripOrigin);
        double[] previous = location(previousStay);
        if (origin == null || previous == null) {
            return areas;
        }
        double previousOriginKm = distanceKm(origin, previous);
        List<AreaAnchorCandidate> forward =
                areas.stream()
                        .filter(
                                area -> {
                                    double[] current = location(area);
                                    return current != null
                                            && distanceKm(origin, current) + 3.0
                                                    >= previousOriginKm;
                                })
                        .toList();
        List<AreaAnchorCandidate> pool = forward.isEmpty() ? areas : forward;
        return pool.stream()
                .sorted(
                        Comparator.comparingDouble(
                                        (AreaAnchorCandidate area) ->
                                                safeDistanceKm(previous, location(area)))
                                .thenComparingDouble(
                                        area -> -safeDistanceKm(origin, location(area))))
                .toList();
    }

    private double[] location(AreaAnchorCandidate area) {
        return area == null ? null : GeoRouteCalculator.parseLocation(area.getLocation());
    }

    private double distanceKm(double[] from, double[] to) {
        return GeoRouteCalculator.distanceKm(from[0], from[1], to[0], to[1]);
    }

    private double safeDistanceKm(double[] from, double[] to) {
        return from == null || to == null ? Double.MAX_VALUE : distanceKm(from, to);
    }

    private String theme(AreaAnchorCandidate area, TravelRequirementDTO requirement) {
        String group = areaGroup(area);
        String suffix =
                switch (group) {
                    case "NATURE" -> "自然休闲";
                    case "HISTORY" -> "历史文化";
                    case "FAMILY" -> "亲子主题";
                    case "WATERFRONT" -> "滨水夜游";
                    case "MUSEUM" -> "博物馆文化";
                    default -> "城市漫游";
                };
        return area.getName() + suffix;
    }

    private String areaGroup(AreaAnchorCandidate area) {
        String text =
                (area.getName() == null ? "" : area.getName())
                        + " "
                        + (area.getArea() == null ? "" : area.getArea())
                        + " "
                        + String.join(" ", area.getTags() == null ? List.of() : area.getTags());
        if (containsAny(text, "自然", "公园", "湖", "湿地", "绿道", "熊猫")) {
            return "NATURE";
        }
        if (containsAny(text, "历史", "文化", "古镇", "寺", "祠", "民国")) {
            return "HISTORY";
        }
        if (containsAny(text, "博物馆", "展览", "科普")) {
            return "MUSEUM";
        }
        if (containsAny(text, "亲子", "乐园", "海洋", "主题")) {
            return "FAMILY";
        }
        if (containsAny(text, "夜景", "游船", "江", "水", "滨水")) {
            return "WATERFRONT";
        }
        return "URBAN";
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String displayDestination(TravelRequirementDTO requirement, CandidatePool pool) {
        if (requirement.getRouteCities() != null && !requirement.getRouteCities().isEmpty()) {
            return requirement.getRouteCities().get(0);
        }
        if (pool != null && pool.getPickupAnchor() != null) {
            return firstNonBlank(
                    pool.getPickupAnchor().getCity(), pool.getPickupAnchor().getArea());
        }
        return firstNonBlank(requirement.getDestination(), "目的地");
    }

    private String preferenceText(TravelRequirementDTO requirement) {
        List<String> values = new ArrayList<>();
        if (requirement.getPreferences() != null) {
            values.addAll(requirement.getPreferences());
        }
        if (requirement.getAvoidances() != null && !requirement.getAvoidances().isEmpty()) {
            values.add("避开：" + String.join("、", requirement.getAvoidances()));
        }
        return values.isEmpty() ? "未特别说明" : String.join("、", values);
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.path(fieldName);
        return value == null || value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }

    private String cleanPlaceName(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("^[\\d.、\\-\\s]+", "").replaceAll("[，,。；;：:].*$", "").trim();
    }

    private boolean matchesPlaceName(String expected, String actual) {
        String a = normalizePlace(expected);
        String b = normalizePlace(actual);
        return !a.isBlank() && !b.isBlank() && (a.contains(b) || b.contains(a));
    }

    private String normalizePlace(String value) {
        return value == null
                ? ""
                : value.replace("市", "").replaceAll("[\\s·・（）()【】\\[\\]-]", "").trim();
    }

    private boolean isBadAnchorName(String value) {
        String text = value == null ? "" : value;
        return containsAny(
                text, "停车场", "停车", "游客中心", "服务中心", "咨询中心", "管理处", "售票", "票务", "入口", "出口", "出入口",
                "卫生间", "公共厕所", "厕所", "洗手间", "公交站", "地铁站", "加油站", "充电站", "商场", "酒店", "餐厅");
    }

    private List<String> splitTags(String value) {
        return value == null || value.isBlank()
                ? List.of()
                : java.util.Arrays.stream(value.split(","))
                        .map(String::trim)
                        .filter(item -> !item.isBlank())
                        .limit(8)
                        .toList();
    }

    private void validateSkeletons(List<DaySkeleton> skeletons, List<String> dayCities, int days) {
        if (skeletons.size() != days) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "路线骨架天数不完整");
        }
        Set<String> usedDayArea = new LinkedHashSet<>();
        for (int index = 0; index < skeletons.size(); index++) {
            DaySkeleton skeleton = skeletons.get(index);
            if (skeleton.getFocusArea() == null) {
                throw new BusinessException(
                        ErrorCode.AI_GENERATE_ERROR, "路线骨架缺少当天主片区，day=" + skeleton.getDay());
            }
            String expectedCity = dayCities.get(index);
            String actualCity = normalizeCity(skeleton.getFocusArea().getCity());
            if (!expectedCity.equals(actualCity)) {
                throw new BusinessException(
                        ErrorCode.AI_GENERATE_ERROR,
                        "路线骨架城市不一致，day=" + skeleton.getDay() + ", city=" + actualCity);
            }
            if (!usedDayArea.add(skeleton.getFocusAreaId())) {
                throw new BusinessException(
                        ErrorCode.AI_GENERATE_ERROR,
                        "路线骨架重复使用片区：" + skeleton.getFocusArea().getName());
            }
            if (index > 0) {
                DaySkeleton previous = skeletons.get(index - 1);
                if (!previous.getStayAreaId().equals(skeleton.getStartAreaId())) {
                    throw new BusinessException(
                            ErrorCode.AI_GENERATE_ERROR,
                            "跨天衔接不一致：Day "
                                    + previous.getDay()
                                    + " 住宿区域必须作为 Day "
                                    + skeleton.getDay()
                                    + " 出发区域");
                }
            }
        }
    }

    private AreaAnchorSnapshot snapshot(AreaAnchorCandidate anchor) {
        return new AreaAnchorSnapshot(
                anchor.getId(),
                anchor.getName(),
                anchor.getRole(),
                anchor.getCity(),
                anchor.getArea(),
                anchor.getAddress(),
                anchor.getLocation());
    }

    private Map<String, Integer> areaCounts(Map<String, List<AreaAnchorCandidate>> areasByCity) {
        Map<String, Integer> result = new LinkedHashMap<>();
        areasByCity.forEach((city, areas) -> result.put(city, areas.size()));
        return result;
    }

    private static boolean usable(AreaAnchorCandidate anchor) {
        return anchor != null && anchor.getLocation() != null && !anchor.getLocation().isBlank();
    }

    private String normalizeCity(String value) {
        return value == null ? "" : value.replace("市", "").replaceAll("\\s+", "").trim();
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private AreaAnchorCandidate firstNonNull(
            AreaAnchorCandidate first, AreaAnchorCandidate second) {
        return first != null ? first : second;
    }
}
