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
        String city = context.getCityProfile().destination();

        for (DayContext dayContext : context.getDayContexts()) {
            String targetArea = dayContext.skeleton().targetArea();
            List<QueryItem> queries =
                    List.of(
                            new QueryItem(
                                    "SCENIC", targetArea + " 景点", city, targetArea, null, null, "查询当天核心景点"),
                            new QueryItem(
                                    "FOOD", targetArea + " 美食", city, targetArea, null, null, "查询午餐和晚餐候选"),
                            new QueryItem(
                                    "HOTEL", dayContext.hotelArea(), city, null, null, null, "确认住宿区域上下文"),
                            new QueryItem(
                                    "TRANSPORT",
                                    null,
                                    city,
                                    null,
                                    dayContext.hotelArea(),
                                    targetArea,
                                    "估算住宿区域到当天核心区域交通"));
            plans.add(new DayQueryPlan(dayContext.day(), queries));
            log.info("节点[day-query-plan]：第 {} 天查询计划={}", dayContext.day(), queries);
        }

        context.setDayQueryPlans(plans);
    }
}
