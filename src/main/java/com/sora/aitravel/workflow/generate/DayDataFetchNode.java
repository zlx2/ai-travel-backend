package com.sora.aitravel.workflow.generate;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 调用工具查询每天需要的真实数据；当前先用日志模拟。 */
@Slf4j
@Component
public class DayDataFetchNode {

    public void execute(GenerateWorkflowContext context) {
        List<DayDataPackage> packages = new ArrayList<>();
        for (DayQueryPlan plan : context.getDayQueryPlans()) {
            for (QueryItem query : plan.queries()) {
                // TODO 按 query.type 调用 ScenicTool/FoodTool/HotelTool/路线工具，并转换为统一候选 DTO。
                log.info(
                        "节点[day-data-fetch]：TODO 调用工具，第 {} 天，type={}, keyword={}, around={}, from={}, to={}",
                        plan.getDay(),
                        query.getType(),
                        query.keyword(),
                        query.around(),
                        query.from(),
                        query.to());
            }

            packages.add(
                    new DayDataPackage(
                            plan.getDay(),
                            context.getCityProfile().scenicCandidates(),
                            context.getCityProfile().foodCandidates(),
                            context.getCityProfile().hotelCandidates(),
                            simulatedRoutes(plan)));
        }
        context.setRankedDayDataPackages(packages);
    }

    private List<TransportRoute> simulatedRoutes(DayQueryPlan plan) {
        return plan.queries().stream()
                .filter(query -> "TRANSPORT".equals(query.getType()))
                .map(
                        query ->
                                new TransportRoute(
                                        query.from(),
                                        query.to(),
                                        "TAXI",
                                        "约 30 分钟",
                                        "约 8 公里",
                                        "SIMULATED_AMAP",
                                        true))
                .toList();
    }
}
