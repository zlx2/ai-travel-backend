package com.sora.aitravel.workflow.generate;

import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.CITY_PROFILE;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.DAY_CONTEXTS;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.DAY_QUERY_PLANS;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.REQUIREMENT;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.model.trip.generate.CityProfile;
import com.sora.aitravel.model.trip.generate.DayContext;
import com.sora.aitravel.model.trip.generate.DayQueryPlan;
import com.sora.aitravel.model.trip.generate.QueryItem;
import com.sora.aitravel.workflow.generate.state.TripGraphStateCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 为每一天生成工具数据查询计划。 */
@Slf4j
@Component
public class DayQueryPlanNode {

    public Map<String, Object> execute(OverAllState state) {
        List<DayQueryPlan> plans =
                buildPlans(
                        TripGraphStateCodec.required(state, CITY_PROFILE, CityProfile.class),
                        TripGraphStateCodec.required(
                                state, REQUIREMENT, TravelRequirementDTO.class),
                        TripGraphStateCodec.optionalList(state, DAY_CONTEXTS, DayContext.class));
        return TripGraphStateCodec.patch(DAY_QUERY_PLANS, plans);
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
            log.info("节点[day-query-plan]：第 {} 天查询计划={}", dayContext.getDay(), queries);
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
}
