package com.sora.aitravel.workflow.generate;

import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.CITY_PROFILE;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.DAY_CONTEXTS;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.LOCKED_DAILY_PLANS;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.RANKED_DAY_DATA_PACKAGES;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.REQUIREMENT;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sora.aitravel.ai.AiGateway;
import com.sora.aitravel.ai.AiScene;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.model.TripPlanDTO;
import com.sora.aitravel.service.PoiIdentityService;
import com.sora.aitravel.service.route.DayRouteOrderService;
import com.sora.aitravel.service.route.GeoRouteCalculator;
import com.sora.aitravel.service.route.PoiClusterer;
import com.sora.aitravel.service.route.RouteLegEstimateFactory;
import com.sora.aitravel.workflow.generate.state.TripGraphStateCodec;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 基于候选 POI 组装每天行程。AI 后续只能在这些候选内选择和解释，不能编造地点。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DayPlanGenerateNode {

    private static final String INTENSITY_LIGHT = "LIGHT";
    private static final String SOURCE_AMAP = "AMAP";
    private static final int MIN_DAILY_SPOTS = 2;
    private static final int MAX_DAILY_SPOTS = 4;
    private static final int LIGHT_DAILY_SPOTS = 3;
    private static final int COMPACT_ROUTE_POOL_LIMIT = 24;
    private static final int AI_DAY_PLAN_CANDIDATE_LIMIT = 4;
    private static final double LONG_RENTAL_DAY_KM = 90.0;
    private static final double LONG_RENTAL_LEG_KM = 85.0;
    private static final double EN_ROUTE_MIN_FROM_START_KM = 8.0;
    private static final double EN_ROUTE_MAX_FROM_START_KM = 70.0;
    private static final double EN_ROUTE_MAX_DETOUR_RATIO = 1.35;
    private static final String DAY_PLAN_AI_PROMPT =
            """
            你是旅游行程审稿节点。路线顺序、跨天去重和时间由系统规则控制，你只做小范围选点确认和推荐理由写作。

            城市：%s
            第 %d 天主题：%s
            期望景点数：%d
            用户偏好：%s
            租车/交通约束：%s
            本次用户追加调整：%s
            已通过规则筛选的候选：
            %s

            任务：
            1. 从候选中选择 %d 个，不能少选，不能换点，不能新增地点；
            2. 必须使用候选里的 id，不允许编造候选外景点；
            3. 如果候选和用户偏好冲突，可以不选该候选，但最终数量必须满足；
            4. 为每个被选景点写一段“为什么推荐”。

            推荐理由写作要求：
            * 像真人旅行顾问推荐，不要像百科或广告；
            * 重点写清楚：游客到了能看到什么、现场有什么意思、为什么值得安排这一站；
            * 不要写评分、开放时间、门票、距离、游玩时长；
            * 不要强调它和前后景点的关联；
            * 不要使用“文化底蕴、历史气息、沉浸式体验、网红打卡、不可错过、适合作为”等空泛词；
            * 不要每段都用“这里、这个景点、它”开头，开头句式要自然变化；
            * 可以从“看点、体验、场景、游客感受、对比特点”任选一个角度切入；
            * 每条 60-90 字。

            只返回 JSON 对象，不要 Markdown：
            {
              "selected": [
                {"id": "c1", "reason": "推荐理由正文"}
              ]
            }
            """;
    private static final RoutePolicy LIGHT_ROUTE_POLICY = new RoutePolicy(8.0, 14.0);
    private static final RoutePolicy DEFAULT_ROUTE_POLICY = new RoutePolicy(14.0, 24.0);
    private static final RoutePolicy RENTAL_CITY_ROUTE_POLICY = new RoutePolicy(18.0, 35.0);
    private static final RoutePolicy RENTAL_SUBURB_ROUTE_POLICY = new RoutePolicy(55.0, 120.0);
    private static final RoutePolicy RENTAL_INTERCITY_ROUTE_POLICY = new RoutePolicy(120.0, 260.0);
    private static final RoutePolicy RENTAL_LONG_ROUTE_POLICY = new RoutePolicy(220.0, 480.0);

    private final PoiClusterer poiClusterer;
    private final PoiIdentityService poiIdentityService;
    private final DayRouteOrderService dayRouteOrderService;
    private final RouteLegEstimateFactory routeLegEstimateFactory;
    private final AiGateway aiGateway;
    private final ObjectMapper objectMapper;

    public Map<String, Object> execute(OverAllState state) {
        List<TripPlanDTO.DailyPlan> dailyPlans =
                generatePlans(
                        new DayPlanInput(
                                TripGraphStateCodec.required(
                                        state, REQUIREMENT, TravelRequirementDTO.class),
                                TripGraphStateCodec.required(
                                        state, CITY_PROFILE, CityProfile.class),
                                TripGraphStateCodec.optionalList(
                                        state, DAY_CONTEXTS, DayContext.class),
                                TripGraphStateCodec.optionalList(
                                        state, RANKED_DAY_DATA_PACKAGES, DayDataPackage.class),
                                TripGraphStateCodec.optionalList(
                                        state, LOCKED_DAILY_PLANS, TripPlanDTO.DailyPlan.class)));
        return TripGraphStateCodec.patch(LOCKED_DAILY_PLANS, dailyPlans);
    }

    private List<TripPlanDTO.DailyPlan> generatePlans(DayPlanInput input) {
        List<TripPlanDTO.DailyPlan> dailyPlans = new ArrayList<>();
        Set<String> usedPoiKeys = usedPoiKeys(input.getLockedDailyPlans());
        for (DayDataPackage dataPackage : input.getRankedDayDataPackages()) {
            DayContext dayContext = findDayContext(input.getDayContexts(), dataPackage.getDay());
            dailyPlans.add(buildDailyPlan(input, dayContext, dataPackage, usedPoiKeys));
        }
        log.info("节点[day-plan-generate]：已生成逐日结构化行程，days={}", dailyPlans.size());
        return dailyPlans;
    }

    private Set<String> usedPoiKeys(List<TripPlanDTO.DailyPlan> existingPlans) {
        Set<String> keys = new HashSet<>();
        if (existingPlans == null) {
            return keys;
        }
        for (TripPlanDTO.DailyPlan plan : existingPlans) {
            if (plan.getSpots() == null) {
                continue;
            }
            plan.getSpots().stream()
                    .map(TripPlanDTO.Spot::getName)
                    .map(poiIdentityService::normalizeName)
                    .filter(key -> !key.isBlank())
                    .forEach(keys::add);
        }
        return keys;
    }

    private List<PoiCandidate> validateAiSelection(
            List<PoiCandidate> candidates, Set<String> usedPoiKeys, DayContext dayContext) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalStateException("第 " + dayContext.getDay() + " 天 AI 未选择景点");
        }
        List<PoiCandidate> duplicated =
                candidates.stream()
                        .filter(candidate -> isUsedCandidate(candidate, usedPoiKeys))
                        .toList();
        if (!duplicated.isEmpty()) {
            throw new IllegalStateException(
                    "第 "
                            + dayContext.getDay()
                            + " 天 AI 选择了已锁定景点："
                            + duplicated.stream().map(PoiCandidate::getName).toList());
        }
        return candidates;
    }

    private boolean isUsedCandidate(PoiCandidate candidate, Set<String> usedPoiKeys) {
        if (candidate == null || usedPoiKeys == null || usedPoiKeys.isEmpty()) {
            return false;
        }
        String key = dedupKey(candidate);
        String name = poiIdentityService.normalizeName(candidate.getName());
        return usedPoiKeys.contains(key)
                || usedPoiKeys.contains(name)
                || usedPoiKeys.stream()
                        .filter(
                                value ->
                                        value != null
                                                && !value.isBlank()
                                                && name != null
                                                && !name.isBlank())
                        .anyMatch(value -> value.contains(name) || name.contains(value));
    }

    private void addUsedCandidate(Set<String> usedPoiKeys, PoiCandidate candidate) {
        if (usedPoiKeys == null || candidate == null) {
            return;
        }
        String key = dedupKey(candidate);
        if (key != null && !key.isBlank()) {
            usedPoiKeys.add(key);
        }
        String name = poiIdentityService.normalizeName(candidate.getName());
        if (name != null && !name.isBlank()) {
            usedPoiKeys.add(name);
        }
    }

    private TripPlanDTO.DailyPlan buildDailyPlan(
            DayPlanInput input,
            DayContext dayContext,
            DayDataPackage dataPackage,
            Set<String> usedPoiKeys) {
        TravelRequirementDTO requirement = input.getRequirement();
        List<PoiCandidate> scenicCandidates =
                dataPackage.scenicCandidates() == null ? List.of() : dataPackage.scenicCandidates();
        int spotCount = spotCount(dayContext, scenicCandidates.size(), requirement);
        List<PoiCandidate> selected =
                selectSpots(
                        scenicCandidates,
                        dayContext,
                        spotCount,
                        usedPoiKeys,
                        wantsNightExperience(requirement, dayContext));
        selected = supplementMinimumSpots(input, selected, dayContext, usedPoiKeys);
        selected =
                preferWorkflowCompactRoute(
                        input, dataPackage, selected, dayContext, usedPoiKeys, spotCount);
        selected =
                supplementEnRouteStopForLongRentalDay(
                        input, dataPackage, selected, dayContext, usedPoiKeys, spotCount);
        AiDayPlan aiDayPlan =
                generateAiDayPlan(requirement, dayContext, selected, usedPoiKeys, spotCount);
        selected = validateAiSelection(aiDayPlan.getSelected(), usedPoiKeys, dayContext);
        selected = repairLongRentalLegs(input, dataPackage, selected, dayContext, usedPoiKeys);
        selected = optimizeRouteOrder(selected, dayContext);
        selected.forEach(candidate -> addUsedCandidate(usedPoiKeys, candidate));
        List<TripPlanDTO.Spot> spots = new ArrayList<>();
        for (int index = 0; index < selected.size(); index++) {
            PoiCandidate candidate = selected.get(index);
            spots.add(
                    toSpot(
                            candidate,
                            index + 1,
                            selected.size(),
                            dayContext,
                            requirement,
                            reasonFor(aiDayPlan, candidate, displayDestination(requirement))));
        }

        PoiCandidate food = first(dataPackage.foodCandidates());
        List<TripPlanDTO.FoodSuggestion> foodSuggestions =
                foodSuggestions(dataPackage.foodCandidates());
        List<TripPlanDTO.RouteLeg> routeLegs = routeLegs(spots, dayContext.rentalEnabled());
        TripPlanDTO.EstimatedCost estimatedCost = estimateCost(requirement, spots, routeLegs, food);
        return new TripPlanDTO.DailyPlan(
                dayContext.getDay(),
                dayContext.skeleton().getTheme(),
                dayContext.skeleton().getIntensity(),
                intensityLabel(dayContext.skeleton().getIntensity()),
                dayCity(dayContext, requirement),
                food == null ? null : firstNonBlank(food.getArea(), food.getName()),
                routeSummary(spots),
                spots,
                routeLegs,
                foodSuggestions,
                dayTips(dayContext, dataPackage),
                estimatedCost,
                null,
                null,
                null);
    }

    private TripPlanDTO.Spot toSpot(
            PoiCandidate candidate,
            int order,
            int totalSpots,
            DayContext dayContext,
            TravelRequirementDTO requirement,
            String recommendationReason) {
        BigDecimal[] lngLat = parseLocation(candidate.getLocation());
        BigDecimal[] entranceLngLat = parseLocation(candidate.getEntranceLocation());
        int duration = durationMinutes(candidate, dayContext.skeleton().getIntensity(), totalSpots);
        TripPlanDTO.Spot spot = new TripPlanDTO.Spot();
        spot.setPoiId(candidate.getSourcePoiId());
        spot.setName(candidate.getName());
        spot.setType(spotType(candidate));
        spot.setCity(dayCity(dayContext, requirement));
        spot.setArea(candidate.getArea());
        spot.setAddress(candidate.getAddress());
        spot.setLng(lngLat[0]);
        spot.setLat(lngLat[1]);
        spot.setCoordType("GCJ02");
        spot.setOrder(order);
        spot.setStartTime(startTime(candidate, order));
        spot.setSuggestedDurationMinutes(duration);
        spot.setSuggestedDurationText(durationText(duration));
        spot.setSuggestedDurationSource("CURATED");
        spot.setReason(recommendationReason);
        spot.setTips(null);
        spot.setTicketCost(null);
        spot.setTicketCostText(null);
        spot.setTicketCostEstimated(false);
        spot.setTicketCostSource("UNAVAILABLE");
        spot.setOpeningHours(candidate.getOpeningHours());
        spot.setRating(parseDecimal(candidate.getRating()));
        spot.setAverageCost(candidate.getAverageCost());
        spot.setBusinessArea(candidate.getBusinessArea());
        spot.setImageUrls(candidate.getImageUrls());
        spot.setEntranceLng(entranceLngLat[0]);
        spot.setEntranceLat(entranceLngLat[1]);
        spot.setReservationRequired(null);
        spot.setTags(spotTags(candidate, dayContext));
        spot.setSource(candidate.getSource());
        spot.setConfidence(
                SOURCE_AMAP.equals(candidate.getSource())
                        ? new BigDecimal("0.90")
                        : new BigDecimal("0.40"));
        return spot;
    }

    private List<TripPlanDTO.RouteLeg> routeLegs(
            List<TripPlanDTO.Spot> spots, boolean rentalEnabled) {
        return routeLegEstimateFactory.build(spots, rentalEnabled);
    }

    private List<TripPlanDTO.FoodSuggestion> foodSuggestions(List<PoiCandidate> foods) {
        if (foods == null || foods.isEmpty()) {
            return List.of();
        }
        List<TripPlanDTO.FoodSuggestion> result = new ArrayList<>();
        result.add(toFoodSuggestion(foods.get(0), "LUNCH"));
        PoiCandidate dinner = foods.size() > 1 ? foods.get(1) : foods.get(0);
        result.add(toFoodSuggestion(dinner, "DINNER"));
        return result;
    }

    private TripPlanDTO.FoodSuggestion toFoodSuggestion(PoiCandidate food, String meal) {
        BigDecimal[] lngLat = parseLocation(food.getLocation());
        return new TripPlanDTO.FoodSuggestion(
                food.getName(),
                food.getArea(),
                meal,
                null,
                parseDecimal(food.getRating()),
                food.getAverageCost(),
                food.getOpeningHours(),
                food.getSource(),
                food.getCity(),
                food.getAddress(),
                lngLat[0],
                lngLat[1],
                "GCJ02");
    }

    private List<String> dayTips(DayContext dayContext, DayDataPackage dataPackage) {
        List<String> tips = new ArrayList<>();
        tips.add("今日节奏：" + intensityLabel(dayContext.skeleton().getIntensity()));
        if (dataPackage.scenicCandidates().stream()
                .anyMatch(item -> !SOURCE_AMAP.equals(item.getSource()))) {
            tips.add("部分地点建议出行前再确认开放信息。");
        }
        if (dayContext.rentalEnabled()) {
            tips.add("本日按租车自驾规划，出发前建议确认停车场、限行和实时路况。");
        }
        return tips;
    }

    private TripPlanDTO.EstimatedCost estimateCost(
            TravelRequirementDTO requirement,
            List<TripPlanDTO.Spot> spots,
            List<TripPlanDTO.RouteLeg> routeLegs,
            PoiCandidate foodCandidate) {
        int people = requirement.getPeopleCount() == null ? 1 : requirement.getPeopleCount();
        int tickets =
                spots.stream()
                                .map(TripPlanDTO.Spot::getTicketCost)
                                .filter(v -> v != null)
                                .reduce(0, Integer::sum)
                        * people;
        int foodPerPerson =
                foodCandidate != null && foodCandidate.getAverageCost() != null
                        ? Math.min(Math.max(foodCandidate.getAverageCost(), 45), 85)
                        : 70;
        int food = foodPerPerson * people;
        int transport =
                routeLegs.stream()
                        .map(TripPlanDTO.RouteLeg::getEstimatedCost)
                        .filter(value -> value != null)
                        .reduce(0, Integer::sum);
        TripPlanDTO.EstimatedCost result = new TripPlanDTO.EstimatedCost();
        result.setTickets(tickets);
        result.setFood(food);
        result.setTransport(transport);
        result.setTotal(tickets + food + transport);
        result.setTicketSource("UNAVAILABLE");
        result.setFoodSource(
                foodCandidate != null && foodCandidate.getAverageCost() != null
                        ? "AMAP_AVERAGE_COST"
                        : "RULE_ESTIMATED");
        result.setTransportSource(
                routeLegs.stream().allMatch(leg -> SOURCE_AMAP.equals(leg.getSource()))
                        ? SOURCE_AMAP
                        : "MIXED");
        result.setExcludesUnknownItems(true);
        return result;
    }

    private List<PoiCandidate> optimizeRouteOrder(
            List<PoiCandidate> selected, DayContext dayContext) {
        return dayRouteOrderService.optimize(selected, dayContext);
    }

    private int routeDistance(PoiCandidate from, PoiCandidate to) {
        return (int) Math.round(poiClusterer.directDistanceKm(from, to) * 1000);
    }

    private String routeLocation(PoiCandidate candidate) {
        return firstNonBlank(candidate.getEntranceLocation(), candidate.getLocation());
    }

    private BigDecimal parseRating(String value) {
        BigDecimal rating = parseDecimal(value);
        return rating == null ? BigDecimal.ZERO : rating;
    }

    private Integer parseInteger(String value) {
        try {
            return value == null || value.isBlank() ? null : Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Integer parseDecimalInteger(String value) {
        try {
            return value == null || value.isBlank()
                    ? null
                    : new BigDecimal(value).setScale(0, java.math.RoundingMode.HALF_UP).intValue();
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String formatDistance(int meters) {
        return meters < 1000
                ? meters + " 米"
                : new BigDecimal(meters)
                                .divide(new BigDecimal("1000"), 1, java.math.RoundingMode.HALF_UP)
                        + " 公里";
    }

    private int spotCount(
            DayContext dayContext, int candidateCount, TravelRequirementDTO requirement) {
        int max = MAX_DAILY_SPOTS;
        int target = Math.min(max, candidateCount);
        if (INTENSITY_LIGHT.equals(dayContext.skeleton().getIntensity())) {
            target = Math.min(target, LIGHT_DAILY_SPOTS);
        }
        return Math.max(MIN_DAILY_SPOTS, target);
    }

    private List<PoiCandidate> selectSpots(
            List<PoiCandidate> candidates,
            DayContext dayContext,
            int limit,
            Set<String> usedPoiKeys,
            boolean includeNight) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<PoiCandidate> fresh =
                candidates.stream()
                        .filter(candidate -> !isUsedCandidate(candidate, usedPoiKeys))
                        .toList();
        List<PoiCandidate> pool = fresh.isEmpty() ? List.of() : fresh;
        if (pool.isEmpty()) {
            return List.of();
        }
        List<PoiCandidate> sorted = pool.stream().sorted(candidateComparator(dayContext)).toList();
        List<PoiCandidate> result = selectDiverse(sorted, limit, includeNight, dayContext);
        result = preferCompactRoute(sorted, result, limit, dayContext);
        if (result.size() < limit) {
            for (PoiCandidate candidate : sorted) {
                if (result.size() >= limit) break;
                if (!result.contains(candidate)
                        && (!isNightCandidate(candidate)
                                || result.stream().noneMatch(this::isNightCandidate))
                        && typeCount(result, spotType(candidate)) < 2
                        && fitsDayCluster(result, candidate, maxDistanceKm(dayContext))) {
                    result.add(candidate);
                }
            }
        }
        result.sort(Comparator.comparing(this::isNightCandidate));
        return result;
    }

    private List<PoiCandidate> preferCompactRoute(
            List<PoiCandidate> candidates,
            List<PoiCandidate> selected,
            int limit,
            DayContext dayContext) {
        if (selected.size() < 2
                || poiClusterer.totalDirectRouteKm(selected) <= maxDailyDirectKm(dayContext)) {
            return selected;
        }
        List<PoiCandidate> compact =
                poiClusterer.bestCluster(candidates, limit, maxDistanceKm(dayContext));
        return compact.size() >= 2
                        && poiClusterer.totalDirectRouteKm(compact)
                                < poiClusterer.totalDirectRouteKm(selected)
                ? compact
                : selected;
    }

    private List<PoiCandidate> supplementMinimumSpots(
            DayPlanInput input,
            List<PoiCandidate> selected,
            DayContext dayContext,
            Set<String> usedPoiKeys) {
        int minimum =
                Math.min(
                        2,
                        spotCount(
                                dayContext,
                                cityScenicCandidates(input.getCityProfile()).size(),
                                input.getRequirement()));
        if (selected.size() >= minimum) {
            return selected;
        }
        List<PoiCandidate> result = new ArrayList<>(selected);
        for (PoiCandidate candidate :
                cityScenicCandidates(input.getCityProfile()).stream()
                        .filter(candidate -> !isUsedCandidate(candidate, usedPoiKeys))
                        .filter(
                                candidate ->
                                        result.stream()
                                                .noneMatch(
                                                        item ->
                                                                dedupKey(item)
                                                                        .equals(
                                                                                dedupKey(
                                                                                        candidate))))
                        .sorted(candidateComparator(dayContext))
                        .toList()) {
            if (result.size() >= minimum) {
                break;
            }
            result.add(candidate);
        }
        return result;
    }

    private List<PoiCandidate> cityScenicCandidates(CityProfile cityProfile) {
        if (cityProfile == null || cityProfile.scenicCandidates() == null) {
            return List.of();
        }
        return cityProfile.scenicCandidates();
    }

    private List<PoiCandidate> selectDiverse(
            List<PoiCandidate> candidates, int limit, boolean includeNight, DayContext dayContext) {
        List<PoiCandidate> result = new ArrayList<>();
        Set<String> types = new HashSet<>();
        PoiCandidate selectedNight =
                includeNight
                        ? candidates.stream()
                                .filter(this::isNightCandidate)
                                .filter(this::isOpenForNight)
                                .max(
                                        Comparator.comparing(
                                                candidate -> parseRating(candidate.getRating())))
                                .orElseGet(
                                        () ->
                                                candidates.stream()
                                                        .filter(this::isNightCandidate)
                                                        .findFirst()
                                                        .orElse(null))
                        : null;
        int daytimeLimit = includeNight ? Math.max(1, limit - 1) : limit;
        for (PoiCandidate candidate : candidates) {
            if (result.size() >= daytimeLimit) break;
            if (isNightCandidate(candidate)) continue;
            String type = spotType(candidate);
            if (!types.contains(type)
                    && fitsDayCluster(result, candidate, maxDistanceKm(dayContext))
                    && fitsNightAnchor(candidate, selectedNight)) {
                result.add(candidate);
                types.add(type);
            }
        }
        for (PoiCandidate candidate : candidates) {
            if (result.size() >= daytimeLimit) break;
            if (!isNightCandidate(candidate)
                    && !result.contains(candidate)
                    && typeCount(result, spotType(candidate)) < 2
                    && fitsDayCluster(result, candidate, maxDistanceKm(dayContext))
                    && fitsNightAnchor(candidate, selectedNight)) {
                result.add(candidate);
            }
        }
        if (selectedNight != null && fitsNightRoute(result, selectedNight)) {
            result.add(selectedNight);
        }
        ensureMinimumDaytimeSpots(
                result, candidates, includeNight ? 2 : Math.min(2, limit), dayContext);
        return result;
    }

    private void ensureMinimumDaytimeSpots(
            List<PoiCandidate> result,
            List<PoiCandidate> candidates,
            int minimumDaytimeSpots,
            DayContext dayContext) {
        while (result.stream().filter(candidate -> !isNightCandidate(candidate)).count()
                < minimumDaytimeSpots) {
            PoiCandidate anchor =
                    result.stream()
                            .filter(candidate -> !isNightCandidate(candidate))
                            .reduce((first, second) -> second)
                            .orElse(null);
            PoiCandidate nearest =
                    candidates.stream()
                            .filter(candidate -> !isNightCandidate(candidate))
                            .filter(candidate -> !result.contains(candidate))
                            .filter(candidate -> typeCount(result, spotType(candidate)) < 2)
                            .filter(
                                    candidate ->
                                            fitsDayCluster(
                                                    result.stream()
                                                            .filter(item -> !isNightCandidate(item))
                                                            .toList(),
                                                    candidate,
                                                    maxDistanceKm(dayContext)))
                            .min(
                                    Comparator.comparingInt(
                                            candidate ->
                                                    anchor == null
                                                            ? 0
                                                            : routeDistance(anchor, candidate)))
                            .orElse(null);
            if (nearest == null) {
                return;
            }
            int nightIndex =
                    java.util.stream.IntStream.range(0, result.size())
                            .filter(index -> isNightCandidate(result.get(index)))
                            .findFirst()
                            .orElse(result.size());
            result.add(nightIndex, nearest);
        }
    }

    private boolean fitsDayCluster(
            List<PoiCandidate> selected, PoiCandidate candidate, double maxKm) {
        return poiClusterer.fitsCluster(selected, candidate, maxKm);
    }

    private boolean fitsNightRoute(List<PoiCandidate> daytimeSpots, PoiCandidate nightCandidate) {
        if (daytimeSpots.isEmpty()) {
            return true;
        }
        return daytimeSpots.stream()
                .allMatch(spot -> poiClusterer.directDistanceKm(spot, nightCandidate) <= 12.0);
    }

    private boolean fitsNightAnchor(PoiCandidate candidate, PoiCandidate selectedNight) {
        return selectedNight == null
                || poiClusterer.directDistanceKm(candidate, selectedNight) <= 12.0;
    }

    private boolean isOpenForNight(PoiCandidate candidate) {
        String hours = candidate.getOpeningHours();
        if (hours == null || hours.isBlank() || hours.contains("24小时")) {
            return true;
        }
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("-(\\d{1,2}):(\\d{2})").matcher(hours);
        int latestClose = 0;
        while (matcher.find()) {
            int hour = Integer.parseInt(matcher.group(1));
            if (hour < 6) {
                hour += 24;
            }
            latestClose = Math.max(latestClose, hour * 60 + Integer.parseInt(matcher.group(2)));
        }
        return latestClose == 0 || latestClose >= 20 * 60;
    }

    private long typeCount(List<PoiCandidate> candidates, String type) {
        return candidates.stream().filter(candidate -> type.equals(spotType(candidate))).count();
    }

    private Comparator<PoiCandidate> candidateComparator(DayContext dayContext) {
        String targetArea = dayContext.skeleton().targetArea();
        return Comparator.comparing((PoiCandidate candidate) -> !matchesArea(candidate, targetArea))
                .thenComparing(
                        candidate ->
                                candidate.getDistanceMeters() == null
                                        ? Integer.MAX_VALUE
                                        : candidate.getDistanceMeters())
                .thenComparing(PoiCandidate::getName);
    }

    private List<PoiCandidate> preferWorkflowCompactRoute(
            DayPlanInput input,
            DayDataPackage dataPackage,
            List<PoiCandidate> selected,
            DayContext dayContext,
            Set<String> usedPoiKeys,
            int limit) {
        double selectedDirectKm = poiClusterer.totalDirectRouteKm(selected);
        if (selected.size() < 2 || selectedDirectKm <= maxDailyDirectKm(dayContext)) {
            return selected;
        }
        List<PoiCandidate> pool =
                mergeCandidates(
                                dataPackage.scenicCandidates(),
                                cityScenicCandidates(input.getCityProfile()))
                        .stream()
                        .filter(candidate -> !isUsedCandidate(candidate, usedPoiKeys))
                        .sorted(candidateComparator(dayContext))
                        .limit(COMPACT_ROUTE_POOL_LIMIT)
                        .toList();
        List<PoiCandidate> compact =
                poiClusterer.bestCluster(
                        pool, Math.min(limit, selected.size()), maxDistanceKm(dayContext));
        double compactDirectKm = poiClusterer.totalDirectRouteKm(compact);
        if (compact.size() >= 2 && compactDirectKm < selectedDirectKm) {
            log.info(
                    "节点[day-plan-generate]：第 {} 天路线过散，已替换为紧凑候选，before={}km, after={}km",
                    dayContext.getDay(),
                    String.format("%.1f", selectedDirectKm),
                    String.format("%.1f", compactDirectKm));
            return compact;
        }
        return selected;
    }

    private List<PoiCandidate> supplementEnRouteStopForLongRentalDay(
            DayPlanInput input,
            DayDataPackage dataPackage,
            List<PoiCandidate> selected,
            DayContext dayContext,
            Set<String> usedPoiKeys,
            int spotCount) {
        if (!dayContext.rentalEnabled()
                || selected == null
                || selected.isEmpty()
                || selected.size() >= spotCount) {
            return selected;
        }
        double[] start = snapshotLocation(dayContext.skeleton().getStartArea());
        if (start == null) {
            return selected;
        }
        PoiCandidate target = farthestFrom(start, selected);
        double[] targetLocation = candidateLocation(target);
        if (targetLocation == null) {
            return selected;
        }
        double directKm =
                GeoRouteCalculator.distanceKm(
                        start[0], start[1], targetLocation[0], targetLocation[1]);
        if (directKm < LONG_RENTAL_DAY_KM) {
            return selected;
        }
        PoiCandidate enRoute =
                mergeCandidates(
                                dataPackage.scenicCandidates(),
                                cityScenicCandidates(input.getCityProfile()))
                        .stream()
                        .filter(candidate -> !isUsedCandidate(candidate, usedPoiKeys))
                        .filter(
                                candidate ->
                                        selected.stream()
                                                .noneMatch(
                                                        item ->
                                                                dedupKey(item)
                                                                        .equals(
                                                                                dedupKey(
                                                                                        candidate))))
                        .filter(
                                candidate ->
                                        enRouteCandidate(
                                                start, targetLocation, directKm, candidate))
                        .max(Comparator.comparing(this::candidateScore))
                        .orElse(null);
        if (enRoute == null) {
            return selected;
        }
        List<PoiCandidate> result = new ArrayList<>(selected);
        result.add(enRoute);
        log.info(
                "节点[day-plan-generate]：第 {} 天为长距离自驾补充沿途景点，target={}, enRoute={}, directKm={}",
                dayContext.getDay(),
                target.getName(),
                enRoute.getName(),
                String.format("%.1f", directKm));
        return result;
    }

    private boolean enRouteCandidate(
            double[] start, double[] target, double directKm, PoiCandidate candidate) {
        double[] location = candidateLocation(candidate);
        if (location == null) {
            return false;
        }
        double fromStart =
                GeoRouteCalculator.distanceKm(start[0], start[1], location[0], location[1]);
        double toTarget =
                GeoRouteCalculator.distanceKm(location[0], location[1], target[0], target[1]);
        if (fromStart < EN_ROUTE_MIN_FROM_START_KM || fromStart > EN_ROUTE_MAX_FROM_START_KM) {
            return false;
        }
        if (toTarget >= directKm) {
            return false;
        }
        return (fromStart + toTarget) <= directKm * EN_ROUTE_MAX_DETOUR_RATIO;
    }

    private int candidateScore(PoiCandidate candidate) {
        BigDecimal rating = parseRating(candidate.getRating());
        Integer distance = candidate.getDistanceMeters();
        return rating.multiply(new BigDecimal("100")).intValue()
                - (distance == null ? 0 : distance / 1000);
    }

    private PoiCandidate farthestFrom(double[] start, List<PoiCandidate> candidates) {
        return candidates.stream()
                .filter(candidate -> candidateLocation(candidate) != null)
                .max(
                        Comparator.comparingDouble(
                                candidate -> {
                                    double[] location = candidateLocation(candidate);
                                    return GeoRouteCalculator.distanceKm(
                                            start[0], start[1], location[0], location[1]);
                                }))
                .orElse(candidates.get(0));
    }

    private double[] snapshotLocation(AreaAnchorSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return GeoRouteCalculator.parseLocation(snapshot.getLocation());
    }

    private double[] candidateLocation(PoiCandidate candidate) {
        if (candidate == null) {
            return null;
        }
        return GeoRouteCalculator.parseLocation(routeLocation(candidate));
    }

    private List<PoiCandidate> mergeCandidates(
            List<PoiCandidate> primary, List<PoiCandidate> fallback) {
        LinkedHashMap<String, PoiCandidate> merged = new LinkedHashMap<>();
        if (primary != null) {
            primary.forEach(candidate -> merged.putIfAbsent(dedupKey(candidate), candidate));
        }
        if (fallback != null) {
            fallback.forEach(candidate -> merged.putIfAbsent(dedupKey(candidate), candidate));
        }
        return new ArrayList<>(merged.values());
    }

    private double maxDistanceKm(DayContext dayContext) {
        return routePolicy(dayContext).getMaxClusterKm();
    }

    private double maxDailyDirectKm(DayContext dayContext) {
        return routePolicy(dayContext).getMaxDirectDailyKm();
    }

    private RoutePolicy routePolicy(DayContext dayContext) {
        if (dayContext.rentalEnabled()) {
            String drivingLimit =
                    dayContext.getDailyDrivingLimit() == null
                            ? ""
                            : dayContext.getDailyDrivingLimit();
            if (drivingLimit.contains("长途") || drivingLimit.contains("6小时")) {
                return RENTAL_LONG_ROUTE_POLICY;
            }
            if (drivingLimit.contains("跨城") || drivingLimit.contains("4-6")) {
                return RENTAL_INTERCITY_ROUTE_POLICY;
            }
            if (drivingLimit.contains("城市短途") || drivingLimit.contains("1-2")) {
                return RENTAL_CITY_ROUTE_POLICY;
            }
            return RENTAL_SUBURB_ROUTE_POLICY;
        }
        return INTENSITY_LIGHT.equals(dayContext.skeleton().getIntensity())
                ? LIGHT_ROUTE_POLICY
                : DEFAULT_ROUTE_POLICY;
    }

    private boolean matchesArea(PoiCandidate candidate, String targetArea) {
        return targetArea != null
                && candidate.getArea() != null
                && (targetArea.contains(candidate.getArea())
                        || candidate.getArea().contains(targetArea));
    }

    private boolean parentFriendly(TravelRequirementDTO requirement) {
        String text =
                String.join(
                                " ",
                                requirement.getPreferences() == null
                                        ? List.of()
                                        : requirement.getPreferences())
                        + " "
                        + String.join(
                                " ",
                                requirement.getAvoidances() == null
                                        ? List.of()
                                        : requirement.getAvoidances());
        return text.contains("父母")
                || text.contains("老人")
                || text.contains("亲子")
                || text.contains("孩子")
                || text.contains("parent")
                || text.contains("relaxed");
    }

    private BigDecimal[] parseLocation(String location) {
        if (location == null || !location.contains(",")) {
            return new BigDecimal[] {null, null};
        }
        String[] parts = location.split(",");
        try {
            return new BigDecimal[] {
                new BigDecimal(parts[0].trim()), new BigDecimal(parts[1].trim())
            };
        } catch (RuntimeException exception) {
            return new BigDecimal[] {null, null};
        }
    }

    private String routeSummary(List<TripPlanDTO.Spot> spots) {
        return String.join(" -> ", spots.stream().map(TripPlanDTO.Spot::getName).toList());
    }

    private String rentalInstruction(DayContext dayContext) {
        if (!dayContext.rentalEnabled()) {
            return "非租车行程，优先控制步行和打车衔接。";
        }
        return dayContext.getRentalInstruction() == null
                ? "租车自驾行程，优先选择自驾顺路、停车便利的地点。"
                : dayContext.getRentalInstruction();
    }

    private String revisionInstruction(DayContext dayContext) {
        String revision = dayContext.getRevisionText();
        return revision == null || revision.isBlank() ? "无" : revision;
    }

    private AiDayPlan generateAiDayPlan(
            TravelRequirementDTO requirement,
            DayContext dayContext,
            List<PoiCandidate> ruleSelected,
            Set<String> usedPoiKeys,
            int spotCount) {
        int requiredCount = Math.min(spotCount, ruleSelected == null ? 0 : ruleSelected.size());
        if (ruleSelected == null || ruleSelected.size() < Math.min(2, spotCount)) {
            throw new IllegalStateException("第 " + dayContext.getDay() + " 天规则选点数量不足");
        }
        String city = dayCity(dayContext, requirement);
        List<AiCandidateRef> refs =
                aiCandidateRefs(
                        ruleSelected.stream().limit(requiredCount).toList(),
                        List.of(),
                        dayContext,
                        usedPoiKeys);
        if (refs.size() < requiredCount) {
            throw new IllegalStateException("第 " + dayContext.getDay() + " 天 AI 输入候选数量不足");
        }
        String prompt =
                DAY_PLAN_AI_PROMPT.formatted(
                        city,
                        dayContext.getDay(),
                        dayContext.skeleton().getTheme(),
                        requiredCount,
                        preferenceText(requirement),
                        rentalInstruction(dayContext),
                        revisionInstruction(dayContext),
                        aiCandidateText(refs, city),
                        requiredCount);
        String response = aiGateway.callJsonObject(AiScene.TRIP_GENERATE, prompt);
        AiDayPlan aiDayPlan = parseAiDayPlan(response, refs, city, requiredCount);
        if (aiDayPlan.getSelected().size() != requiredCount) {
            throw new IllegalStateException(
                    "第 "
                            + dayContext.getDay()
                            + " 天 AI 选点数量不完整，expected="
                            + requiredCount
                            + ", actual="
                            + aiDayPlan.getSelected().size());
        }
        return aiDayPlan;
    }

    private List<PoiCandidate> repairLongRentalLegs(
            DayPlanInput input,
            DayDataPackage dataPackage,
            List<PoiCandidate> selected,
            DayContext dayContext,
            Set<String> usedPoiKeys) {
        if (!dayContext.rentalEnabled() || selected == null || selected.size() < 2) {
            return selected;
        }
        List<PoiCandidate> ordered = optimizeRouteOrder(selected, dayContext);
        List<PoiCandidate> result = new ArrayList<>(ordered);
        for (int index = 0; index < result.size() - 1 && result.size() < MAX_DAILY_SPOTS; index++) {
            PoiCandidate from = result.get(index);
            PoiCandidate to = result.get(index + 1);
            double[] fromLocation = candidateLocation(from);
            double[] toLocation = candidateLocation(to);
            if (fromLocation == null || toLocation == null) {
                continue;
            }
            double legKm =
                    GeoRouteCalculator.distanceKm(
                            fromLocation[0], fromLocation[1], toLocation[0], toLocation[1]);
            if (legKm <= LONG_RENTAL_LEG_KM) {
                continue;
            }
            PoiCandidate midpoint =
                    midpointCandidate(
                            input,
                            dataPackage,
                            result,
                            usedPoiKeys,
                            fromLocation,
                            toLocation,
                            legKm);
            if (midpoint == null) {
                continue;
            }
            result.add(index + 1, midpoint);
            log.info(
                    "节点[day-plan-generate]：第 {} 天修复过长单段路线，from={}, to={}, midpoint={}, beforeKm={}",
                    dayContext.getDay(),
                    from.getName(),
                    to.getName(),
                    midpoint.getName(),
                    String.format("%.1f", legKm));
        }
        return optimizeRouteOrder(result, dayContext);
    }

    private PoiCandidate midpointCandidate(
            DayPlanInput input,
            DayDataPackage dataPackage,
            List<PoiCandidate> selected,
            Set<String> usedPoiKeys,
            double[] from,
            double[] to,
            double legKm) {
        return mergeCandidates(
                        dataPackage.scenicCandidates(),
                        cityScenicCandidates(input.getCityProfile()))
                .stream()
                .filter(candidate -> !isUsedCandidate(candidate, usedPoiKeys))
                .filter(
                        candidate ->
                                selected.stream()
                                        .noneMatch(
                                                item -> dedupKey(item).equals(dedupKey(candidate))))
                .filter(candidate -> midpointCandidate(from, to, legKm, candidate))
                .max(Comparator.comparing(this::candidateScore))
                .orElse(null);
    }

    private boolean midpointCandidate(
            double[] from, double[] to, double legKm, PoiCandidate candidate) {
        double[] location = candidateLocation(candidate);
        if (location == null) {
            return false;
        }
        double fromKm = GeoRouteCalculator.distanceKm(from[0], from[1], location[0], location[1]);
        double toKm = GeoRouteCalculator.distanceKm(location[0], location[1], to[0], to[1]);
        double maxSplitKm = Math.max(fromKm, toKm);
        if (fromKm < EN_ROUTE_MIN_FROM_START_KM || toKm < EN_ROUTE_MIN_FROM_START_KM) {
            return false;
        }
        if (maxSplitKm >= legKm || maxSplitKm > LONG_RENTAL_LEG_KM) {
            return false;
        }
        return (fromKm + toKm) <= legKm * EN_ROUTE_MAX_DETOUR_RATIO;
    }

    private List<AiCandidateRef> aiCandidateRefs(
            List<PoiCandidate> candidates,
            List<PoiCandidate> ruleSelected,
            DayContext dayContext,
            Set<String> usedPoiKeys) {
        LinkedHashMap<String, PoiCandidate> merged = new LinkedHashMap<>();
        if (ruleSelected != null) {
            ruleSelected.forEach(candidate -> merged.putIfAbsent(dedupKey(candidate), candidate));
        }
        if (candidates != null) {
            candidates.stream()
                    .filter(candidate -> !isUsedCandidate(candidate, usedPoiKeys))
                    .sorted(candidateComparator(dayContext))
                    .forEach(candidate -> merged.putIfAbsent(dedupKey(candidate), candidate));
        }
        List<AiCandidateRef> refs = new ArrayList<>();
        int index = 1;
        for (PoiCandidate candidate : merged.values()) {
            if (refs.size() >= AI_DAY_PLAN_CANDIDATE_LIMIT) {
                break;
            }
            refs.add(new AiCandidateRef("c" + index, candidate));
            index++;
        }
        return refs;
    }

    private String aiCandidateText(List<AiCandidateRef> refs, String city) {
        List<String> lines = new ArrayList<>();
        for (AiCandidateRef ref : refs) {
            PoiCandidate candidate = ref.getCandidate();
            lines.add(
                    "- id="
                            + ref.getId()
                            + "；名称="
                            + nullToEmpty(candidate.getName())
                            + "；类型="
                            + spotType(candidate)
                            + "；区域="
                            + nullToEmpty(candidate.getArea())
                            + "；坐标="
                            + nullToEmpty(routeLocation(candidate))
                            + "；参考信息="
                            + cleanReference(candidate, city));
        }
        return String.join("\n", lines);
    }

    private AiDayPlan parseAiDayPlan(
            String json, List<AiCandidateRef> refs, String city, int spotCount) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode selectedNode = root.path("selected");
            if (!selectedNode.isArray()) {
                throw new IllegalStateException("AI 未返回 selected 数组");
            }
            LinkedHashMap<String, PoiCandidate> byId = new LinkedHashMap<>();
            refs.forEach(ref -> byId.put(ref.getId(), ref.getCandidate()));
            List<PoiCandidate> selected = new ArrayList<>();
            LinkedHashMap<String, String> reasons = new LinkedHashMap<>();
            for (JsonNode item : selectedNode) {
                if (selected.size() >= spotCount) {
                    break;
                }
                String id = text(item, "id");
                PoiCandidate candidate = byId.get(id);
                if (candidate == null || selected.contains(candidate)) {
                    continue;
                }
                selected.add(candidate);
                String reason = cleanRecommendation(text(item, "reason"));
                if (reason.isBlank()) {
                    throw new IllegalStateException("AI 未返回景点推荐理由，spot=" + candidate.getName());
                }
                reasons.put(dedupKey(candidate), reason);
            }
            return new AiDayPlan(selected, reasons);
        } catch (Exception exception) {
            throw new IllegalStateException("AI 当日计划 JSON 解析失败，json=" + json, exception);
        }
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

    private String experienceReference(PoiCandidate candidate, String destination) {
        String name = candidate.getName() == null ? "这里" : candidate.getName();
        return switch (spotType(candidate)) {
            case "NIGHT_MARKET" -> "夜间餐饮、街头小吃、城市烟火气";
            case "NIGHT_VIEW" -> "夜间观景、城市灯光、拍照视角";
            case "MUSEUM" -> "主题展陈、地方记忆、工艺或历史线索";
            case "PARK" -> "自然风景、步道、树荫、水面或山景";
            case "HISTORIC_AREA" -> "街巷、建筑、碑刻、人物故事和在地生活";
            case "SCENIC" -> destination + "代表性风景、观景点、散步和拍照";
            default -> name + "的主要看点、现场体验和游客感受";
        };
    }

    private String cleanReference(PoiCandidate candidate, String city) {
        List<String> parts = new ArrayList<>();
        parts.add(experienceReference(candidate, city));
        if (candidate.getBusinessTags() != null && !candidate.getBusinessTags().isEmpty()) {
            parts.add(
                    "标签："
                            + String.join(
                                    "、", candidate.getBusinessTags().stream().limit(4).toList()));
        }
        if (candidate.getAddress() != null && !candidate.getAddress().isBlank()) {
            parts.add("位置线索：" + candidate.getAddress());
        }
        return String.join("；", parts);
    }

    private String cleanRecommendation(String text) {
        String cleaned =
                text == null
                        ? ""
                        : text.replaceAll("(?m)^#+\\s*", "")
                                .replaceAll("(?m)^推荐理由[:：]\\s*", "")
                                .replaceAll("^([“\"'])(.*)\\1$", "$2")
                                .replaceAll("\\s+", " ")
                                .trim();
        return cleaned.length() > 120 ? cleaned.substring(0, 120) : cleaned;
    }

    private String reasonFor(AiDayPlan plan, PoiCandidate candidate, String city) {
        String reason = plan.getReasons().get(dedupKey(candidate));
        if (reason == null || reason.isBlank()) {
            return fallbackReason(candidate, city);
        }
        return reason;
    }

    private String fallbackReason(PoiCandidate candidate, String city) {
        return firstNonBlank(candidate.getReason(), experienceReference(candidate, city))
                + "，适合放在当天顺路行程中停留体验。";
    }

    private String experienceLabel(PoiCandidate candidate) {
        return switch (spotType(candidate)) {
            case "NIGHT_MARKET" -> "夜市美食";
            case "NIGHT_VIEW" -> "夜景";
            case "MUSEUM" -> "历史文化";
            case "PARK" -> "自然休闲";
            case "HISTORIC_AREA" -> "街区漫步";
            case "SCENIC" -> "代表性景观";
            default -> "城市代表";
        };
    }

    private String startTime(PoiCandidate candidate, int order) {
        if (isNightCandidate(candidate)) {
            return "19:00";
        }
        return switch (order) {
            case 1 -> "09:30";
            case 2 -> "11:20";
            case 3 -> "14:10";
            case 4 -> "16:10";
            case 5 -> "18:00";
            default -> "19:00";
        };
    }

    private boolean wantsNightExperience(TravelRequirementDTO requirement, DayContext dayContext) {
        if (requirement.getPreferences() == null) return false;
        boolean wantsNight =
                requirement.getPreferences().stream()
                        .anyMatch(item -> item.contains("夜景") || item.contains("夜市"));
        int nightDay = requirement.getDays() == 1 ? 1 : 2;
        return wantsNight && dayContext.getDay() == nightDay;
    }

    private boolean isNightCandidate(PoiCandidate candidate) {
        String name = candidate.getName() == null ? "" : candidate.getName();
        return "NIGHT".equals(candidate.getCategory())
                || name.contains("夜市")
                || name.contains("夜景")
                || name.contains("夜游");
    }

    private String spotType(PoiCandidate candidate) {
        String name = candidate.getName() == null ? "" : candidate.getName();
        if (isNightCandidate(candidate)) return name.contains("夜市") ? "NIGHT_MARKET" : "NIGHT_VIEW";
        if (name.contains("博物馆")
                || name.contains("博物院")
                || name.contains("纪念馆")
                || name.contains("美术馆")) return "MUSEUM";
        if (name.contains("公园") || name.contains("湿地") || name.contains("植物园")) return "PARK";
        if (name.contains("古镇") || name.contains("古街") || name.contains("街区"))
            return "HISTORIC_AREA";
        if (name.contains("山") || name.contains("景区") || name.contains("风景区")) return "SCENIC";
        return "LANDMARK";
    }

    private int durationMinutes(PoiCandidate candidate, String intensity, int totalSpots) {
        String name = candidate.getName() == null ? "" : candidate.getName();
        int minutes;
        if (name.contains("乐园")
                || name.contains("动物园")
                || name.contains("海洋")
                || name.contains("熊猫")) {
            minutes = 240;
        } else if (name.contains("山")
                || name.contains("风景区")
                || name.contains("景区")
                || name.contains("古镇")) {
            minutes = 180;
        } else if (name.contains("博物馆")
                || name.contains("纪念馆")
                || name.contains("美术馆")
                || name.contains("寺")) {
            minutes = 120;
        } else if (name.contains("公园") || name.contains("湿地") || name.contains("植物园")) {
            minutes = 120;
        } else if (name.contains("街") || name.contains("广场") || name.contains("商圈")) {
            minutes = 90;
        } else {
            minutes = 120;
        }
        if ("LIGHT".equals(intensity) && totalSpots <= 2) {
            minutes += 30;
        }
        if (totalSpots >= 3) {
            minutes = Math.min(minutes, compactDayDurationCap(candidate));
        }
        return minutes;
    }

    private int compactDayDurationCap(PoiCandidate candidate) {
        String name = candidate.getName() == null ? "" : candidate.getName();
        if (name.contains("乐园")
                || name.contains("动物园")
                || name.contains("海洋")
                || name.contains("熊猫")) {
            return 180;
        }
        if (name.contains("山")
                || name.contains("风景区")
                || name.contains("景区")
                || name.contains("古镇")) {
            return 150;
        }
        if (name.contains("博物馆")
                || name.contains("纪念馆")
                || name.contains("美术馆")
                || name.contains("寺")) {
            return 110;
        }
        if (name.contains("公园") || name.contains("湿地") || name.contains("植物园")) {
            return 100;
        }
        if (name.contains("街") || name.contains("广场") || name.contains("商圈")) {
            return 80;
        }
        return 110;
    }

    private String durationText(int minutes) {
        return minutes % 60 == 0 ? "约" + (minutes / 60) + "小时" : "约" + (minutes / 60.0) + "小时";
    }

    private BigDecimal parseDecimal(String value) {
        try {
            return value == null || value.isBlank() ? null : new BigDecimal(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String intensityLabel(String intensity) {
        return switch (intensity) {
            case "LIGHT" -> "轻松";
            case "TIGHT" -> "紧凑";
            default -> "适中";
        };
    }

    private List<String> spotTags(PoiCandidate candidate, DayContext dayContext) {
        List<String> tags = new ArrayList<>();
        tags.add(dayContext.skeleton().getIntensity());
        tags.add(experienceLabel(candidate).replace("体验", ""));
        return tags.stream().distinct().limit(5).toList();
    }

    private PoiCandidate first(List<PoiCandidate> candidates) {
        return candidates == null || candidates.isEmpty() ? null : candidates.get(0);
    }

    private DayContext findDayContext(List<DayContext> dayContexts, Integer day) {
        return dayContexts.stream()
                .filter(item -> item.getDay().equals(day))
                .findFirst()
                .orElseThrow();
    }

    private String displayDestination(TravelRequirementDTO requirement) {
        if (requirement.getDestination() != null && !requirement.getDestination().isBlank()) {
            return requirement.getDestination();
        }
        if (requirement.getRouteRegion() != null && !requirement.getRouteRegion().isBlank()) {
            return requirement.getRouteRegion();
        }
        return String.join("-", requirement.getRouteCities());
    }

    private String dayCity(DayContext dayContext, TravelRequirementDTO requirement) {
        String targetArea =
                dayContext == null || dayContext.skeleton() == null
                        ? null
                        : dayContext.skeleton().targetArea();
        if (targetArea != null && !targetArea.isBlank()) {
            String city = targetArea.replaceAll("(核心城区|休闲街区|夜间活跃区域|自然景区周边|老城与美食街区|热门游览区域)$", "");
            if (!city.isBlank()) {
                return city;
            }
        }
        return displayDestination(requirement);
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private String dedupKey(PoiCandidate candidate) {
        return poiIdentityService.dedupKey(candidate);
    }

    private static final class AiCandidateRef {
        private final String id;
        private final PoiCandidate candidate;

        private AiCandidateRef(String id, PoiCandidate candidate) {
            this.id = id;
            this.candidate = candidate;
        }

        private String getId() {
            return id;
        }

        private PoiCandidate getCandidate() {
            return candidate;
        }
    }

    private static final class AiDayPlan {
        private final List<PoiCandidate> selected;
        private final LinkedHashMap<String, String> reasons;

        private AiDayPlan(List<PoiCandidate> selected, LinkedHashMap<String, String> reasons) {
            this.selected = selected;
            this.reasons = reasons;
        }

        private static AiDayPlan empty() {
            return new AiDayPlan(List.of(), new LinkedHashMap<>());
        }

        private List<PoiCandidate> getSelected() {
            return selected;
        }

        private LinkedHashMap<String, String> getReasons() {
            return reasons;
        }
    }

    private static final class DayPlanInput {
        private final TravelRequirementDTO requirement;
        private final CityProfile cityProfile;
        private final List<DayContext> dayContexts;
        private final List<DayDataPackage> rankedDayDataPackages;
        private final List<TripPlanDTO.DailyPlan> lockedDailyPlans;

        private DayPlanInput(
                TravelRequirementDTO requirement,
                CityProfile cityProfile,
                List<DayContext> dayContexts,
                List<DayDataPackage> rankedDayDataPackages,
                List<TripPlanDTO.DailyPlan> lockedDailyPlans) {
            this.requirement = requirement;
            this.cityProfile = cityProfile;
            this.dayContexts = dayContexts == null ? List.of() : dayContexts;
            this.rankedDayDataPackages =
                    rankedDayDataPackages == null ? List.of() : rankedDayDataPackages;
            this.lockedDailyPlans = lockedDailyPlans == null ? List.of() : lockedDailyPlans;
        }

        private TravelRequirementDTO getRequirement() {
            return requirement;
        }

        private CityProfile getCityProfile() {
            return cityProfile;
        }

        private List<DayContext> getDayContexts() {
            return dayContexts;
        }

        private List<DayDataPackage> getRankedDayDataPackages() {
            return rankedDayDataPackages;
        }

        private List<TripPlanDTO.DailyPlan> getLockedDailyPlans() {
            return lockedDailyPlans;
        }
    }

    private static final class RoutePolicy {
        private final double maxClusterKm;
        private final double maxDirectDailyKm;

        private RoutePolicy(double maxClusterKm, double maxDirectDailyKm) {
            this.maxClusterKm = maxClusterKm;
            this.maxDirectDailyKm = maxDirectDailyKm;
        }

        private double getMaxClusterKm() {
            return maxClusterKm;
        }

        private double getMaxDirectDailyKm() {
            return maxDirectDailyKm;
        }
    }
}
