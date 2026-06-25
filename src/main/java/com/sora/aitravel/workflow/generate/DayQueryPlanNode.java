package com.sora.aitravel.workflow.generate;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 为每一天生成工具数据查询计划。 */
@Slf4j
@Component
public class DayQueryPlanNode {

    public void execute(GenerateWorkflowContext context) {
        List<DayQueryPlan> plans = new ArrayList<>();
        String city = context.getCityProfile().getDestination();
        boolean wantsNight = hasPreference(context, "夜景") || hasPreference(context, "夜市");
        int nightDay = context.getRequirement().getDays() == 1 ? 1 : 2;

        for (DayContext dayContext : context.getDayContexts()) {
            String targetArea = dayContext.skeleton().targetArea();
            List<QueryItem> queries = new ArrayList<>();
            for (String keyword : scenicKeywords(city, dayContext)) {
                queries.add(
                        new QueryItem(
                                "SCENIC",
                                keyword,
                                city,
                                targetArea,
                                null,
                                null,
                                "查询当天核心景点"));
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
                            "HOTEL",
                            dayContext.hotelArea(),
                            city,
                            null,
                            null,
                            null,
                            "确认住宿区域上下文"));
            queries.add(
                    new QueryItem(
                            "TRANSPORT",
                            null,
                            city,
                            null,
                            dayContext.hotelArea(),
                            targetArea,
                            "估算住宿区域到当天核心区域交通"));
            plans.add(new DayQueryPlan(dayContext.getDay(), queries));
            log.info("节点[day-query-plan]：第 {} 天查询计划={}", dayContext.getDay(), queries);
        }

        context.setDayQueryPlans(plans);
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

    private String foodKeyword(DayContext dayContext) {
        return dayContext.skeleton().getTheme().contains("美食") ? "美食街" : "特色餐厅";
    }

    private boolean hasPreference(GenerateWorkflowContext context, String keyword) {
        return context.getRequirement().getPreferences() != null
                && context.getRequirement().getPreferences().stream()
                        .anyMatch(item -> item.contains(keyword));
    }
}
