package com.sora.aitravel.workflow.generate;

import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.CITY_PROFILE;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.DAY_CONTEXTS;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.DAY_QUERY_PLANS;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.DAY_SKELETONS;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.LOCKED_DAILY_PLANS;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.PREVIOUS_DAILY_PLANS;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.RENTAL_TRIP_CONTEXT;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.REQUIREMENT;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.REVISION_TEXT;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.SELECTED_QUOTE;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.TARGET_DAY_NO;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.config.AiGateway;
import com.sora.aitravel.dto.model.RentalQuoteOptionDTO;
import com.sora.aitravel.dto.model.RentalTripContextDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.model.CityProfile;
import com.sora.aitravel.model.DayContext;
import com.sora.aitravel.model.DayQueryPlan;
import com.sora.aitravel.model.DaySkeleton;
import com.sora.aitravel.model.QueryItem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 构建每一天生成行程所需的上下文。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DayInputPrepareNode {

    private static final String AI_SCENIC_RECOMMEND_PROMPT =
            """
            为单日旅行推荐 8-12 个真实地点名，用于高德 POI 搜索。
            城市=%s；day=%d；主片区=%s；主题=%s；偏好=%s；已用=%s；租车=%s；修改=%s
            规则：只给游客会去的景点/街区/公园/博物馆/古镇/自然景区；不要泛词、餐厅、酒店、商场、停车场、入口、游客中心、厕所、管理处；不要拆同一景区内部小点；尽量覆盖 2-3 个游览单元；避开已用片区。只返回 JSON：
            {"places":["地点1","地点2"]}
            """;

    private final AiGateway aiGateway;
    private final ObjectMapper objectMapper;

    public Map<String, Object> execute(OverAllState state) {
        Map<String, Object> patch = new LinkedHashMap<>();
        Map<String, Object> contextPatch = buildDayContexts(state);
        patch.putAll(contextPatch);

        OverAllState contextState = TripGraphStateCodec.withPatch(state, patch);
        Map<String, Object> filterPatch = filterTargetDay(contextState);
        patch.putAll(filterPatch);

        OverAllState filteredState = TripGraphStateCodec.withPatch(state, patch);
        patch.putAll(buildQueryPlans(filteredState));
        patch.putAll(snapshotPreviousDays(filteredState));
        return patch;
    }

    private Map<String, Object> buildDayContexts(OverAllState state) {
        List<DaySkeleton> daySkeletons =
                TripGraphStateCodec.optionalList(state, DAY_SKELETONS, DaySkeleton.class);
        if (daySkeletons.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "缺少逐日行程骨架，无法生成单日行程");
        }
        TravelRequirementDTO requirement =
                TripGraphStateCodec.required(state, REQUIREMENT, TravelRequirementDTO.class);
        RentalQuoteOptionDTO selectedQuote =
                TripGraphStateCodec.optional(state, SELECTED_QUOTE, RentalQuoteOptionDTO.class)
                        .orElse(null);
        RentalTripContextDTO rentalTripContext =
                TripGraphStateCodec.optional(state, RENTAL_TRIP_CONTEXT, RentalTripContextDTO.class)
                        .orElse(null);
        CityProfile cityProfile =
                TripGraphStateCodec.optional(state, CITY_PROFILE, CityProfile.class).orElse(null);
        String revisionText =
                TripGraphStateCodec.optional(state, REVISION_TEXT, String.class).orElse(null);

        List<DayContext> dayContexts = new ArrayList<>();
        List<String> usedPlaces = new ArrayList<>();
        for (DaySkeleton skeleton : daySkeletons) {
            dayContexts.add(
                    new DayContext(
                            skeleton.getDay(),
                            skeleton,
                            List.copyOf(usedPlaces),
                            stayAreaName(skeleton),
                            requirement.getPace(),
                            selectedQuote != null,
                            rentalInstruction(selectedQuote, rentalTripContext),
                            rentalTripContext == null
                                    ? null
                                    : rentalTripContext.getRouteStructure(),
                            rentalTripContext == null
                                    ? null
                                    : rentalTripContext.getDailyDrivingLimit(),
                            rentalTripContext == null ? null : rentalTripContext.getArrivalMode(),
                            rentalTripContext == null
                                    ? null
                                    : rentalTripContext.getArrivalTimeRange(),
                            dayStartTime(skeleton.getDay(), rentalTripContext),
                            maxSpotCount(skeleton.getDay(), rentalTripContext),
                            arrivalConstraint(skeleton.getDay(), rentalTripContext),
                            revisionText));
            usedPlaces.add(skeleton.targetArea());
        }

        log.info("节点[day-input-prepare]：已构建每天上下文，count={}", dayContexts.size());
        return TripGraphStateCodec.patch(DAY_CONTEXTS, dayContexts);
    }

    private Map<String, Object> filterTargetDay(OverAllState state) {
        Integer dayNo = TripGraphStateCodec.required(state, TARGET_DAY_NO, Integer.class);
        List<DayContext> filtered =
                TripGraphStateCodec.optionalList(state, DAY_CONTEXTS, DayContext.class).stream()
                        .filter(dayContext -> dayContext.getDay().equals(dayNo))
                        .toList();
        if (filtered.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "行程天数不存在：" + dayNo);
        }
        return TripGraphStateCodec.patch(DAY_CONTEXTS, filtered);
    }

    private Map<String, Object> buildQueryPlans(OverAllState state) {
        List<DayQueryPlan> plans =
                buildPlans(
                        TripGraphStateCodec.required(state, CITY_PROFILE, CityProfile.class),
                        TripGraphStateCodec.required(
                                state, REQUIREMENT, TravelRequirementDTO.class),
                        TripGraphStateCodec.optionalList(state, DAY_CONTEXTS, DayContext.class));
        return TripGraphStateCodec.patch(DAY_QUERY_PLANS, plans);
    }

    private Map<String, Object> snapshotPreviousDays(OverAllState state) {
        List<com.sora.aitravel.dto.model.TripPlanDTO.DailyPlan> lockedDailyPlans =
                TripGraphStateCodec.optionalList(
                        state,
                        LOCKED_DAILY_PLANS,
                        com.sora.aitravel.dto.model.TripPlanDTO.DailyPlan.class);
        return TripGraphStateCodec.patch(PREVIOUS_DAILY_PLANS, lockedDailyPlans);
    }

    private List<DayQueryPlan> buildPlans(
            CityProfile cityProfile,
            TravelRequirementDTO requirement,
            List<DayContext> dayContexts) {
        List<DayQueryPlan> plans = new ArrayList<>();
        String city = cityProfile.getDestination();

        for (DayContext dayContext : dayContexts) {
            String targetArea = dayContext.skeleton().targetArea();
            List<QueryItem> queries = new ArrayList<>();
            for (String place : aiScenicPlaces(city, requirement, dayContext)) {
                queries.add(
                        new QueryItem(
                                "AI_SCENIC",
                                city + " " + place,
                                city,
                                targetArea,
                                null,
                                null,
                                "AI 推荐地点，高德 POI 校验后作为当天优先景点候选"));
            }
            for (String keyword : scenicKeywords(city, dayContext)) {
                queries.add(
                        new QueryItem("SCENIC", keyword, city, targetArea, null, null, "查询当天核心景点"));
            }
            if (dayContext.rentalEnabled()) {
                for (String keyword : rentalScenicKeywords(city, dayContext)) {
                    queries.add(
                            new QueryItem(
                                    "SELF_DRIVE",
                                    keyword,
                                    city,
                                    targetArea,
                                    null,
                                    null,
                            "租车自驾补充查询，优先匹配周边、停车便利或公共交通不便但值得去的地点"));
                }
            }
            for (String keyword : nightKeywords(city, dayContext)) {
                queries.add(
                        new QueryItem(
                                "NIGHT",
                                keyword,
                                city,
                                targetArea,
                                null,
                                null,
                                "查询晚餐后的真实夜间景点或夜游体验"));
            }
            queries.add(
                    new QueryItem(
                            "FOOD",
                            city + " " + foodKeyword(dayContext),
                            city,
                            targetArea,
                            null,
                            null,
                            "查询午餐和晚餐候选"));
            queries.add(
                    new QueryItem(
                            "TRANSPORT",
                            null,
                            city,
                            null,
                            dayContext.hotelArea(),
                            targetArea,
                            dayContext.rentalEnabled()
                                    ? "估算住宿/接车区域到当天核心区域自驾交通"
                                    : "估算住宿区域到当天核心区域交通"));
            plans.add(new DayQueryPlan(dayContext.getDay(), queries));
            log.info("节点[day-input-prepare]：第 {} 天查询计划={}", dayContext.getDay(), queries);
        }
        return plans;
    }

    private List<String> aiScenicPlaces(
            String city, TravelRequirementDTO requirement, DayContext dayContext) {
        try {
            String prompt =
                    AI_SCENIC_RECOMMEND_PROMPT.formatted(
                            city,
                            dayContext.getDay(),
                            dayContext.skeleton().targetArea(),
                            dayContext.skeleton().getTheme(),
                            preferenceText(requirement),
                            dayContext.getUsedPlaces() == null
                                    ? "无"
                                    : String.join("、", dayContext.getUsedPlaces()),
                            firstNonBlank(dayContext.getRentalInstruction(), "无"),
                            firstNonBlank(dayContext.getRevisionText(), "无"));
            String json = aiGateway.callJsonObject("AI 景点推荐", prompt);
            JsonNode places = objectMapper.readTree(json).path("places");
            if (!places.isArray()) {
                return List.of();
            }
            List<String> result = new ArrayList<>();
            for (JsonNode item : places) {
                String place = cleanPlaceName(item.asText());
                if (!place.isBlank()
                        && result.stream().noneMatch(existing -> samePlace(existing, place))) {
                    result.add(place);
                }
                if (result.size() >= 12) {
                    break;
                }
            }
            log.info("节点[day-input-prepare]：第 {} 天 AI 推荐地点={}", dayContext.getDay(), result);
            return result;
        } catch (Exception exception) {
            log.warn(
                    "节点[day-input-prepare]：第 {} 天 AI 推荐地点失败，降级使用关键词查询，reason={}",
                    dayContext.getDay(),
                    exception.getMessage());
            return List.of();
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

    private String cleanPlaceName(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("^[\\d.、\\-\\s]+", "").replaceAll("[，,。；;：:].*$", "").trim();
    }

    private boolean samePlace(String first, String second) {
        String a = normalizePlace(first);
        String b = normalizePlace(second);
        return a.equals(b) || a.contains(b) || b.contains(a);
    }

    private String normalizePlace(String value) {
        return value == null ? "" : value.replaceAll("[\\s·・（）()【】\\[\\]-]", "").trim();
    }

    private List<String> scenicKeywords(String city, DayContext dayContext) {
        if (dayContext.getDay() == 1) {
            return List.of(city + " 城市地标", city + " 博物馆", city + " 历史街区");
        }
        if (dayContext.skeleton().getTheme().contains("返程")) {
            return List.of(city + " 公园", city + " 古镇", city + " 休闲街区");
        }
        if (dayContext.skeleton().getTheme().contains("自然")) {
            return List.of(city + " 风景名胜", city + " 公园", city + " 自然景区");
        }
        return List.of(city + " 必游景点", city + " 名胜古迹", city + " 历史街区");
    }

    private List<String> rentalScenicKeywords(String city, DayContext dayContext) {
        if (dayContext.getDay() == 1) {
            return List.of(city + " 近郊自驾", city + " 停车方便景区");
        }
        String routeStructure =
                dayContext.getRouteStructure() == null ? "" : dayContext.getRouteStructure();
        if (routeStructure.contains("多城市") || routeStructure.contains("环线")) {
            return List.of(city + " 周边城市", city + " 自驾路线");
        }
        return List.of(city + " 周边游", city + " 古镇", city + " 自然景区");
    }

    private List<String> nightKeywords(String city, DayContext dayContext) {
        String targetArea = dayContext.skeleton().targetArea();
        List<String> keywords = new ArrayList<>();
        if (targetArea != null && !targetArea.isBlank()) {
            keywords.add(city + " " + targetArea + " 夜游");
            keywords.add(city + " " + targetArea + " 夜景");
        }
        keywords.add(city + " 夜游");
        keywords.add(city + " 夜景");
        keywords.add(city + " 夜市");
        return keywords.stream().distinct().limit(4).toList();
    }

    private String foodKeyword(DayContext dayContext) {
        return dayContext.skeleton().getTheme().contains("美食") ? "美食街" : "特色餐厅";
    }

    private boolean hasPreference(TravelRequirementDTO requirement, String keyword) {
        return requirement.getPreferences() != null
                && requirement.getPreferences().stream().anyMatch(item -> item.contains(keyword));
    }

    private String stayAreaName(DaySkeleton skeleton) {
        if (skeleton == null || skeleton.getStayArea() == null) {
            return null;
        }
        return firstNonBlank(skeleton.getStayArea().getName(), skeleton.getStayArea().getArea());
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private String rentalInstruction(
            RentalQuoteOptionDTO selectedQuote, RentalTripContextDTO rentalTripContext) {
        if (selectedQuote == null || rentalTripContext == null) {
            return null;
        }
        String vehicle =
                firstNonBlank(selectedQuote.getDisplayName(), selectedQuote.getGroupName());
        String pickup =
                rentalTripContext.getPickupPlan() == null
                        ? null
                        : rentalTripContext.getPickupPlan().getDisplayText();
        String arrival =
                rentalTripContext.getArrivalPoint() == null
                        ? null
                        : rentalTripContext.getArrivalPoint().getName();
        return "本次为租车自驾行程，已选车辆："
                + firstNonBlank(vehicle, "租车套餐")
                + "；到达/接车点："
                + firstNonBlank(arrival, "目的地到达点")
                + "；接车安排："
                + firstNonBlank(pickup, "送车接人后开始自驾")
                + "；游玩范围："
                + firstNonBlank(rentalTripContext.getRouteStructure(), "城市+周边")
                + "；驾驶强度："
                + firstNonBlank(rentalTripContext.getDailyDrivingLimit(), "近郊自驾（单日累计约2-4小时）")
                + "。选点应优先考虑自驾顺路、停车便利、城市周边自然/古镇等有车更方便到达的地点。";
    }

    private String dayStartTime(Integer dayNo, RentalTripContextDTO rentalTripContext) {
        if (!isFirstDay(dayNo) || rentalTripContext == null) {
            return null;
        }
        String range = safe(rentalTripContext.getArrivalTimeRange());
        if (isEveningArrival(range)) {
            return "19:00";
        }
        if (isAfternoonArrival(range)) {
            return "15:30";
        }
        if (isNoonArrival(range)) {
            return "13:30";
        }
        return null;
    }

    private Integer maxSpotCount(Integer dayNo, RentalTripContextDTO rentalTripContext) {
        if (!isFirstDay(dayNo) || rentalTripContext == null) {
            return null;
        }
        String range = safe(rentalTripContext.getArrivalTimeRange());
        if (isEveningArrival(range)) {
            return 1;
        }
        if (isAfternoonArrival(range) || isNoonArrival(range)) {
            return 2;
        }
        return null;
    }

    private String arrivalConstraint(Integer dayNo, RentalTripContextDTO rentalTripContext) {
        if (!isFirstDay(dayNo) || rentalTripContext == null) {
            return null;
        }
        String range = safe(rentalTripContext.getArrivalTimeRange());
        if (isEveningArrival(range)) {
            return "首日按晚上到达处理：不要安排上午或下午游览，只安排接车、入住、晚餐、附近夜景或休息，景点最多 1 个。";
        }
        if (isAfternoonArrival(range)) {
            return "首日按下午到达处理：不要安排上午游览，下午后轻量游玩，景点最多 2 个。";
        }
        if (isNoonArrival(range)) {
            return "首日按中午到达处理：上午不安排游览，午后开始轻量游玩，景点最多 2 个。";
        }
        return null;
    }

    private boolean isFirstDay(Integer dayNo) {
        return dayNo != null && dayNo == 1;
    }

    private boolean isEveningArrival(String value) {
        String text = safe(value).toLowerCase();
        return text.contains("晚上") || text.contains("夜") || text.contains("night");
    }

    private boolean isAfternoonArrival(String value) {
        String text = safe(value).toLowerCase();
        return text.contains("下午") || text.contains("傍晚") || text.contains("afternoon");
    }

    private boolean isNoonArrival(String value) {
        String text = safe(value).toLowerCase();
        return text.contains("中午") || text.contains("午间") || text.contains("noon");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
