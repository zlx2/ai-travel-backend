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
import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.dto.model.RentalQuoteOptionDTO;
import com.sora.aitravel.dto.model.RentalTripContextDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.model.CityProfile;
import com.sora.aitravel.model.DayContext;
import com.sora.aitravel.model.DayQueryPlan;
import com.sora.aitravel.model.DaySkeleton;
import com.sora.aitravel.model.PoiCandidate;
import com.sora.aitravel.model.QueryItem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 构建每一天生成行程所需的上下文。 */
@Slf4j
@Component
public class DayInputPrepareNode {

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
        String hotelArea = resolveHotelArea(cityProfile, requirement);

        for (DaySkeleton skeleton : daySkeletons) {
            dayContexts.add(
                    new DayContext(
                            skeleton.getDay(),
                            skeleton,
                            List.copyOf(usedPlaces),
                            hotelArea,
                            requirement.getPace(),
                            selectedQuote != null,
                            rentalInstruction(selectedQuote, rentalTripContext),
                            rentalTripContext == null
                                    ? null
                                    : rentalTripContext.getRouteStructure(),
                            rentalTripContext == null
                                    ? null
                                    : rentalTripContext.getDailyDrivingLimit(),
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
        boolean wantsNight = hasPreference(requirement, "夜景") || hasPreference(requirement, "夜市");
        int nightDay = requirement.getDays() == 1 ? 1 : 2;

        for (DayContext dayContext : dayContexts) {
            String targetArea = dayContext.skeleton().targetArea();
            List<QueryItem> queries = new ArrayList<>();
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
            if (wantsNight && dayContext.getDay() == nightDay) {
                queries.add(
                        new QueryItem(
                                "NIGHT",
                                city + " 夜市",
                                city,
                                targetArea,
                                null,
                                null,
                                "匹配用户夜市偏好，安排晚间烟火气体验"));
                queries.add(
                        new QueryItem(
                                "NIGHT",
                                city + " 夜景",
                                city,
                                targetArea,
                                null,
                                null,
                                "匹配用户夜景偏好，安排晚间游览"));
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
                            "HOTEL", dayContext.hotelArea(), city, null, null, null, "确认住宿区域上下文"));
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

    private String foodKeyword(DayContext dayContext) {
        return dayContext.skeleton().getTheme().contains("美食") ? "美食街" : "特色餐厅";
    }

    private boolean hasPreference(TravelRequirementDTO requirement, String keyword) {
        return requirement.getPreferences() != null
                && requirement.getPreferences().stream().anyMatch(item -> item.contains(keyword));
    }

    private String resolveHotelArea(CityProfile profile, TravelRequirementDTO requirement) {
        if (profile != null
                && profile.hotelCandidates() != null
                && !profile.hotelCandidates().isEmpty()) {
            PoiCandidate hotel = profile.hotelCandidates().get(0);
            return firstNonBlank(hotel.getName(), hotel.getArea());
        }
        if (profile != null
                && profile.getPopularAreas() != null
                && !profile.getPopularAreas().isEmpty()) {
            return profile.getPopularAreas().get(0);
        }
        if (requirement != null) {
            return firstNonBlank(requirement.getDestination(), requirement.getRouteRegion());
        }
        return null;
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
}
