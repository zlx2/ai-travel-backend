package com.sora.aitravel.workflow.generate;

import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.CANDIDATE_POOL;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.DAY_SKELETONS;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.REQUIREMENT;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.SELECTED_QUOTE;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.dto.model.RentalQuoteOptionDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.model.AreaAnchorCandidate;
import com.sora.aitravel.model.AreaAnchorSnapshot;
import com.sora.aitravel.model.CandidatePool;
import com.sora.aitravel.model.DaySkeleton;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Builds the route skeleton from normalized city/area anchors before any spot filling happens. */
@Slf4j
@Component
public class MacroRoutePrepareNode {

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

        for (int index = 0; index < dayCities.size(); index++) {
            int dayNo = index + 1;
            String city = dayCities.get(index);
            AreaAnchorCandidate focus =
                    chooseFocusArea(
                            areasByCity.get(city),
                            usedAreaIdsByCity.computeIfAbsent(city, key -> new LinkedHashSet<>()),
                            previousGroupByCity.get(city));
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
            List<AreaAnchorCandidate> areas, Set<String> usedAreaIds, String previousGroup) {
        if (areas == null || areas.isEmpty()) {
            return null;
        }
        return areas.stream()
                .filter(area -> !usedAreaIds.contains(area.getId()))
                .filter(area -> previousGroup == null || !previousGroup.equals(areaGroup(area)))
                .findFirst()
                .orElseGet(
                        () ->
                                areas.stream()
                                        .filter(area -> !usedAreaIds.contains(area.getId()))
                                        .findFirst()
                                        .orElse(areas.get(0)));
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
