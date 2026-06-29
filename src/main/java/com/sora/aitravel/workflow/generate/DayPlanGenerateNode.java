package com.sora.aitravel.workflow.generate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sora.aitravel.ai.AiGateway;
import com.sora.aitravel.ai.AiScene;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.model.TripPlanDTO;
import com.sora.aitravel.dto.model.route.Path;
import com.sora.aitravel.dto.model.route.Route;
import com.sora.aitravel.service.AmapApiService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
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
    private static final String MODE_WALK = "WALK";
    private static final String MODE_TAXI = "TAXI";
    private static final String MODE_DRIVING = "DRIVING";
    private static final String SOURCE_AMAP = "AMAP";
    private static final String SOURCE_ESTIMATED = "ESTIMATED";
    private static final double WALKING_ROUTE_MAX_KM = 2.0;
    private static final int MIN_DAILY_SPOTS = 2;
    private static final int MAX_DAILY_SPOTS = 4;
    private static final int LIGHT_DAILY_SPOTS = 3;
    private static final int COMPACT_ROUTE_POOL_LIMIT = 24;
    private static final int AI_DAY_PLAN_CANDIDATE_LIMIT = 14;
    private static final String DAY_PLAN_AI_PROMPT =
            """
            你是旅游行程顾问。请只基于候选列表，为当天行程选择景点并为每个被选景点写“为什么推荐”。

            城市：%s
            第 %d 天主题：%s
            期望景点数：%d
            用户偏好：%s
            租车/交通约束：%s
            本次用户追加调整：%s
            候选景点：
            %s

            任务：
            1. 从候选景点中选择 %d 个，优先兼顾看点质量、当天主题、不要重复、同一天路线不要太散；
            2. 必须使用候选里的 id，不允许编造候选外景点；
            3. 如果“本次用户追加调整”不为空，必须优先满足；如要求减少步行、减少跨区、指定餐饮偏好，应体现在选点和理由中；
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

    private final AmapApiService amapApiService;
    private final AiGateway aiGateway;
    private final ObjectMapper objectMapper;

    public void execute(GenerateWorkflowContext context) {
        List<TripPlanDTO.DailyPlan> dailyPlans = new ArrayList<>();
        Set<String> usedPoiKeys = usedPoiKeys(context.getLockedDailyPlans());
        for (DayDataPackage dataPackage : context.getRankedDayDataPackages()) {
            DayContext dayContext = findDayContext(context, dataPackage.getDay());
            dailyPlans.add(buildDailyPlan(context, dayContext, dataPackage, usedPoiKeys));
        }
        context.setLockedDailyPlans(dailyPlans);
        log.info("节点[day-plan-generate]：已生成逐日结构化行程，days={}", dailyPlans.size());
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
                    .map(this::normalizePoiName)
                    .filter(key -> !key.isBlank())
                    .forEach(keys::add);
        }
        return keys;
    }

    private TripPlanDTO.DailyPlan buildDailyPlan(
            GenerateWorkflowContext context,
            DayContext dayContext,
            DayDataPackage dataPackage,
            Set<String> usedPoiKeys) {
        TravelRequirementDTO requirement = context.getRequirement();
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
        selected = supplementMinimumSpots(context, selected, dayContext, usedPoiKeys);
        selected = optimizeOrder(selected);
        selected =
                preferWorkflowCompactRoute(
                        context, dataPackage, selected, dayContext, usedPoiKeys, spotCount);
        AiDayPlan aiDayPlan =
                generateAiDayPlan(
                        context, dayContext, dataPackage, selected, usedPoiKeys, spotCount);
        selected = aiDayPlan.selected();
        selected = optimizeOrder(selected);
        selected.forEach(candidate -> usedPoiKeys.add(dedupKey(candidate)));
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
        List<TripPlanDTO.RouteLeg> routeLegs = routeLegs(spots, dayContext.rentalEnabled());
        TripPlanDTO.EstimatedCost estimatedCost = estimateCost(requirement, spots, routeLegs, food);
        return new TripPlanDTO.DailyPlan(
                dayContext.getDay(),
                dayContext.skeleton().getTheme(),
                dayContext.skeleton().getIntensity(),
                intensityLabel(dayContext.skeleton().getIntensity()),
                displayDestination(requirement),
                food == null ? null : firstNonBlank(food.getArea(), food.getName()),
                routeSummary(spots),
                spots,
                routeLegs,
                foodSuggestions(food),
                dayTips(dayContext, dataPackage),
                estimatedCost);
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
        spot.setCity(displayDestination(requirement));
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

    private List<TripPlanDTO.RouteLeg> routeLegs(List<TripPlanDTO.Spot> spots, boolean rentalEnabled) {
        List<TripPlanDTO.RouteLeg> legs = new ArrayList<>();
        for (int index = 0; index < spots.size() - 1; index++) {
            TripPlanDTO.Spot from = spots.get(index);
            TripPlanDTO.Spot to = spots.get(index + 1);
            RouteMetric metric = fetchRouteMetric(location(from), location(to), rentalEnabled);
            TripPlanDTO.RouteLeg leg = new TripPlanDTO.RouteLeg();
            leg.setFromOrder(from.getOrder());
            leg.setToOrder(to.getOrder());
            leg.setMode(metric.getMode());
            leg.setSuggestion(
                    "从"
                            + from.getName()
                            + "前往"
                            + to.getName()
                            + "，"
                            + (rentalEnabled ? "建议自驾，" : "")
                            + metric.getDescription());
            leg.setDistanceMeters(metric.getDistanceMeters());
            leg.setDurationMinutes(metric.getDurationMinutes());
            leg.setEstimatedCost(metric.getEstimatedCost());
            leg.setSource(metric.getSource());
            legs.add(leg);
        }
        return legs;
    }

    private List<TripPlanDTO.FoodSuggestion> foodSuggestions(PoiCandidate food) {
        if (food == null) {
            return List.of();
        }
        return List.of(
                new TripPlanDTO.FoodSuggestion(
                        food.getName(),
                        food.getArea(),
                        "LUNCH",
                        null,
                        parseDecimal(food.getRating()),
                        food.getAverageCost(),
                        food.getOpeningHours(),
                        food.getSource()));
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

    private List<PoiCandidate> optimizeOrder(List<PoiCandidate> selected) {
        if (selected.size() < 2) {
            return selected;
        }
        List<PoiCandidate> remaining = new ArrayList<>(selected);
        List<PoiCandidate> ordered = new ArrayList<>();
        PoiCandidate current =
                remaining.stream()
                        .filter(candidate -> !isNightCandidate(candidate))
                        .min(
                                Comparator.comparingInt(this::closingMinute)
                                        .thenComparing(
                                                candidate -> parseRating(candidate.getRating()),
                                                Comparator.reverseOrder()))
                        .orElse(remaining.get(0));
        ordered.add(current);
        remaining.remove(current);
        while (!remaining.isEmpty()) {
            PoiCandidate from = current;
            PoiCandidate next =
                    remaining.stream()
                            .min(
                                    Comparator.comparing(this::isNightCandidate)
                                            .thenComparingInt(
                                                    candidate -> routeDistance(from, candidate)))
                            .orElseThrow();
            ordered.add(next);
            remaining.remove(next);
            current = next;
        }
        return ordered;
    }

    private int routeDistance(PoiCandidate from, PoiCandidate to) {
        return (int)
                Math.round(coordinateDistanceKm(routeLocation(from), routeLocation(to)) * 1000);
    }

    private RouteMetric fetchRouteMetric(String origin, String destination, boolean rentalEnabled) {
        if (origin == null || destination == null) {
            return new RouteMetric(
                    Integer.MAX_VALUE / 4,
                    null,
                    null,
                    "UNKNOWN",
                    rentalEnabled ? "坐标不足，自驾路线待前端地图计算。" : "建议按当天实际位置灵活选择步行或打车。",
                    SOURCE_ESTIMATED);
        }
        double directKm = coordinateDistanceKm(origin, destination);
        try {
            if (!rentalEnabled && directKm <= WALKING_ROUTE_MAX_KM) {
                var response = amapApiService.walkingRoute(origin, destination);
                RouteMetric metric =
                        routeMetric(response == null ? null : response.getData(), MODE_WALK);
                if (metric != null) {
                    return metric;
                }
            }
            var response = amapApiService.drivingRoute(origin, destination);
            RouteMetric metric =
                    routeMetric(
                            response == null ? null : response.getData(),
                            rentalEnabled ? MODE_DRIVING : MODE_TAXI);
            if (metric != null) {
                return metric;
            }
        } catch (RuntimeException exception) {
            log.warn("高德路线查询失败，origin={}, destination={}", origin, destination, exception);
        }
        int fallbackDistance = (int) Math.round(directKm * 1000);
        return new RouteMetric(
                fallbackDistance,
                null,
                null,
                rentalEnabled ? MODE_DRIVING : MODE_TAXI,
                rentalEnabled
                        ? "建议自驾衔接，出发前按实时路况和停车条件调整。"
                        : directKm <= WALKING_ROUTE_MAX_KM
                                ? "距离较近，建议步行或短途打车。"
                                : "建议打车衔接，出发前按实时路况调整。",
                SOURCE_ESTIMATED);
    }

    private RouteMetric routeMetric(Route route, String mode) {
        if (route == null || route.getPaths() == null || route.getPaths().isEmpty()) {
            return null;
        }
        Path path = route.getPaths().get(0);
        Integer distance = parseInteger(path.getDistance());
        String durationText =
                path.getCost() == null ? path.getDuration() : path.getCost().getDuration();
        Integer durationSeconds = parseInteger(durationText);
        Integer durationMinutes =
                durationSeconds == null ? null : (int) Math.ceil(durationSeconds / 60.0);
        if (distance == null || durationMinutes == null) {
            return null;
        }
        Integer cost =
                MODE_WALK.equals(mode)
                        ? 0
                        : MODE_DRIVING.equals(mode)
                                ? estimateDrivingCost(distance)
                                : parseDecimalInteger(route.getTaxiCost());
        String description =
                formatDistance(distance)
                        + "，约 "
                        + durationMinutes
                        + " 分钟"
                        + (MODE_TAXI.equals(mode) && cost != null ? "，打车约 ¥" + cost : "")
                        + (MODE_DRIVING.equals(mode) && cost != null ? "，自驾能耗/油费约 ¥" + cost : "");
        return new RouteMetric(distance, durationMinutes, cost, mode, description, SOURCE_AMAP);
    }

    private Integer estimateDrivingCost(Integer distanceMeters) {
        if (distanceMeters == null) {
            return null;
        }
        return Math.max(3, (int) Math.ceil(distanceMeters / 1000.0 * 0.8));
    }

    private String routeLocation(PoiCandidate candidate) {
        return firstNonBlank(candidate.getEntranceLocation(), candidate.getLocation());
    }

    private String location(TripPlanDTO.Spot spot) {
        BigDecimal lng = spot.getEntranceLng() == null ? spot.getLng() : spot.getEntranceLng();
        BigDecimal lat = spot.getEntranceLat() == null ? spot.getLat() : spot.getEntranceLat();
        return lng == null || lat == null ? null : lng + "," + lat;
    }

    private double coordinateDistanceKm(String first, String second) {
        BigDecimal[] from = parseLocation(first);
        BigDecimal[] to = parseLocation(second);
        if (from[0] == null || from[1] == null || to[0] == null || to[1] == null) {
            return 1000;
        }
        return distanceKm(from[0], from[1], to[0], to[1]);
    }

    private int closingMinute(PoiCandidate candidate) {
        String openingHours = candidate.getOpeningHours();
        if (openingHours == null) {
            return isNightCandidate(candidate) ? 24 * 60 : 20 * 60;
        }
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("(\\d{1,2}):(\\d{2})\\s*$").matcher(openingHours);
        if (!matcher.find()) {
            return isNightCandidate(candidate) ? 24 * 60 : 20 * 60;
        }
        return Integer.parseInt(matcher.group(1)) * 60 + Integer.parseInt(matcher.group(2));
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
                        .filter(candidate -> !usedPoiKeys.contains(dedupKey(candidate)))
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
        if (selected.size() < 2 || totalDirectRouteKm(selected) <= maxDailyDirectKm(dayContext)) {
            return selected;
        }
        List<PoiCandidate> compact = bestCluster(candidates, limit, maxDistanceKm(dayContext));
        return compact.size() >= 2 && totalDirectRouteKm(compact) < totalDirectRouteKm(selected)
                ? compact
                : selected;
    }

    private double totalDirectRouteKm(List<PoiCandidate> candidates) {
        double total = 0;
        for (int index = 0; index < candidates.size() - 1; index++) {
            total +=
                    coordinateDistanceKm(
                            routeLocation(candidates.get(index)),
                            routeLocation(candidates.get(index + 1)));
        }
        return total;
    }

    private List<PoiCandidate> supplementMinimumSpots(
            GenerateWorkflowContext context,
            List<PoiCandidate> selected,
            DayContext dayContext,
            Set<String> usedPoiKeys) {
        int minimum =
                Math.min(
                        2,
                        spotCount(
                                dayContext,
                                cityScenicCandidates(context).size(),
                                context.getRequirement()));
        if (selected.size() >= minimum) {
            return selected;
        }
        List<PoiCandidate> result = new ArrayList<>(selected);
        for (PoiCandidate candidate :
                cityScenicCandidates(context).stream()
                        .filter(candidate -> !usedPoiKeys.contains(dedupKey(candidate)))
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

    private List<PoiCandidate> cityScenicCandidates(GenerateWorkflowContext context) {
        if (context.getCityProfile() == null
                || context.getCityProfile().scenicCandidates() == null) {
            return List.of();
        }
        return context.getCityProfile().scenicCandidates();
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
        if (selected.isEmpty()) {
            return true;
        }
        return selected.stream()
                .allMatch(
                        item ->
                                coordinateDistanceKm(routeLocation(item), routeLocation(candidate))
                                        <= maxKm);
    }

    private boolean fitsNightRoute(List<PoiCandidate> daytimeSpots, PoiCandidate nightCandidate) {
        if (daytimeSpots.isEmpty()) {
            return true;
        }
        return daytimeSpots.stream()
                .allMatch(
                        spot ->
                                coordinateDistanceKm(
                                                routeLocation(spot), routeLocation(nightCandidate))
                                        <= 12.0);
    }

    private boolean fitsNightAnchor(PoiCandidate candidate, PoiCandidate selectedNight) {
        return selectedNight == null
                || coordinateDistanceKm(routeLocation(candidate), routeLocation(selectedNight))
                        <= 12.0;
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

    private List<PoiCandidate> bestCluster(List<PoiCandidate> candidates, int limit, double maxKm) {
        List<PoiCandidate> best = List.of();
        for (PoiCandidate anchor : candidates) {
            BigDecimal[] anchorLocation = parseLocation(anchor.getLocation());
            if (anchorLocation[0] == null || anchorLocation[1] == null) {
                continue;
            }
            List<PoiCandidate> cluster = new ArrayList<>();
            for (PoiCandidate candidate : candidates) {
                BigDecimal[] location = parseLocation(candidate.getLocation());
                if (location[0] == null || location[1] == null) {
                    continue;
                }
                if (distanceKm(anchorLocation[0], anchorLocation[1], location[0], location[1])
                        <= maxKm) {
                    cluster.add(candidate);
                }
                if (cluster.size() >= limit) {
                    break;
                }
            }
            if (cluster.size() > best.size()) {
                best = cluster;
            }
            if (best.size() >= limit) {
                return best;
            }
        }
        return best;
    }

    private List<PoiCandidate> preferWorkflowCompactRoute(
            GenerateWorkflowContext context,
            DayDataPackage dataPackage,
            List<PoiCandidate> selected,
            DayContext dayContext,
            Set<String> usedPoiKeys,
            int limit) {
        double selectedDirectKm = totalDirectRouteKm(selected);
        if (selected.size() < 2 || selectedDirectKm <= maxDailyDirectKm(dayContext)) {
            return selected;
        }
        List<PoiCandidate> pool =
                mergeCandidates(dataPackage.scenicCandidates(), cityScenicCandidates(context))
                        .stream()
                        .filter(candidate -> !usedPoiKeys.contains(dedupKey(candidate)))
                        .sorted(candidateComparator(dayContext))
                        .limit(COMPACT_ROUTE_POOL_LIMIT)
                        .toList();
        List<PoiCandidate> compact =
                bestCluster(pool, Math.min(limit, selected.size()), maxDistanceKm(dayContext));
        double compactDirectKm = totalDirectRouteKm(compact);
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
            String drivingLimit = dayContext.getDailyDrivingLimit() == null ? "" : dayContext.getDailyDrivingLimit();
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

    private double distanceKm(BigDecimal lng1, BigDecimal lat1, BigDecimal lng2, BigDecimal lat2) {
        double radius = 6371.0;
        double latRad1 = Math.toRadians(lat1.doubleValue());
        double latRad2 = Math.toRadians(lat2.doubleValue());
        double deltaLat = Math.toRadians(lat2.doubleValue() - lat1.doubleValue());
        double deltaLng = Math.toRadians(lng2.doubleValue() - lng1.doubleValue());
        double a =
                Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                        + Math.cos(latRad1)
                                * Math.cos(latRad2)
                                * Math.sin(deltaLng / 2)
                                * Math.sin(deltaLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return radius * c;
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
            GenerateWorkflowContext context,
            DayContext dayContext,
            DayDataPackage dataPackage,
            List<PoiCandidate> ruleSelected,
            Set<String> usedPoiKeys,
            int spotCount) {
        List<AiCandidateRef> refs =
                aiCandidateRefs(
                        dataPackage.scenicCandidates(), ruleSelected, dayContext, usedPoiKeys);
        if (refs.isEmpty()) {
            throw new IllegalStateException("第 " + dayContext.getDay() + " 天没有可用真实景点候选");
        }
        TravelRequirementDTO requirement = context.getRequirement();
        String city = displayDestination(requirement);
        String prompt =
                DAY_PLAN_AI_PROMPT.formatted(
                        city,
                        dayContext.getDay(),
                        dayContext.skeleton().getTheme(),
                        spotCount,
                        preferenceText(requirement),
                        rentalInstruction(dayContext),
                        revisionInstruction(dayContext),
                        aiCandidateText(refs, city),
                        spotCount);
        try {
            String json = aiGateway.callJsonObject(AiScene.TRIP_GENERATE, prompt);
            AiDayPlan plan = parseAiDayPlan(json, refs, city, spotCount);
            if (plan.selected().size() < Math.min(2, spotCount)) {
                throw new IllegalStateException(
                        "AI 选点数量不足，day="
                                + dayContext.getDay()
                                + ", selected="
                                + plan.selected().size());
            }
            return plan;
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "AI 当日选点/推荐理由生成失败，day=" + dayContext.getDay(), exception);
        }
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
                    .filter(candidate -> !usedPoiKeys.contains(dedupKey(candidate)))
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
            PoiCandidate candidate = ref.candidate();
            lines.add(
                    "- id="
                            + ref.id()
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
            refs.forEach(ref -> byId.put(ref.id(), ref.candidate()));
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
        String reason = plan.reasons().get(dedupKey(candidate));
        if (reason == null || reason.isBlank()) {
            throw new IllegalStateException("AI 推荐理由缺失，spot=" + candidate.getName());
        }
        return reason;
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
        if ("LIGHT".equals(intensity)) {
            minutes += 30;
        }
        return minutes;
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

    private DayContext findDayContext(GenerateWorkflowContext context, Integer day) {
        return context.getDayContexts().stream()
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
        if (candidate == null) {
            return "";
        }
        if (candidate.getName() == null) {
            return "";
        }
        return normalizePoiName(candidate.getName());
    }

    private String normalizePoiName(String name) {
        return name.replaceAll("[（(].*?[）)]", "")
                .replaceAll("[-—·].*$", "")
                .replace("景区", "")
                .replace("风景区", "")
                .replace("步行街", "")
                .replace("历史文化特色街区", "历史街区")
                .replace("历史文化街区", "历史街区")
                .replace("特色街区", "街区")
                .replaceAll("\\s+", "")
                .trim();
    }

    private record AiCandidateRef(String id, PoiCandidate candidate) {}

    private record AiDayPlan(List<PoiCandidate> selected, LinkedHashMap<String, String> reasons) {
        private static AiDayPlan empty() {
            return new AiDayPlan(List.of(), new LinkedHashMap<>());
        }
    }

    private static final class RouteMetric {
        private final Integer distanceMeters;
        private final Integer durationMinutes;
        private final Integer estimatedCost;
        private final String mode;
        private final String description;
        private final String source;

        private RouteMetric(
                Integer distanceMeters,
                Integer durationMinutes,
                Integer estimatedCost,
                String mode,
                String description,
                String source) {
            this.distanceMeters = distanceMeters;
            this.durationMinutes = durationMinutes;
            this.estimatedCost = estimatedCost;
            this.mode = mode;
            this.description = description;
            this.source = source;
        }

        private Integer getDistanceMeters() {
            return distanceMeters;
        }

        private Integer getDurationMinutes() {
            return durationMinutes;
        }

        private Integer getEstimatedCost() {
            return estimatedCost;
        }

        private String getMode() {
            return mode;
        }

        private String getDescription() {
            return description;
        }

        private String getSource() {
            return source;
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
