package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.dto.model.TripPlanDTO;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** AI 基于工具候选数据生成每天行程；当前先用日志和规则模拟。 */
@Slf4j
@Component
public class DayPlanGenerateNode {

    public void execute(GenerateWorkflowContext context) {
        List<TripPlanDTO.DailyPlan> dailyPlans = new ArrayList<>();
        for (DayDataPackage dataPackage : context.getRankedDayDataPackages()) {
            DayContext dayContext = findDayContext(context, dataPackage.day());
            // TODO 调用 ChatModel：输入 requirement、dayContext、候选 POI、交通路线，只允许使用候选数据。
            log.info(
                    "节点[day-plan-generate]：TODO 调用 AI 基于真实候选生成第 {} 天行程，scenic={}, food={}, routes={}",
                    dataPackage.day(),
                    names(dataPackage.scenicCandidates()),
                    names(dataPackage.foodCandidates()),
                    dataPackage.transportRoutes());

            dailyPlans.add(buildDailyPlan(context, dayContext, dataPackage));
        }
        context.setLockedDailyPlans(dailyPlans);
        context.setRawModelResponse("SIMULATED_DAY_PLANS_GENERATED");
    }

    private TripPlanDTO.DailyPlan buildDailyPlan(
            GenerateWorkflowContext context, DayContext dayContext, DayDataPackage dataPackage) {
        PoiCandidate morning = first(dataPackage.scenicCandidates());
        PoiCandidate afternoon =
                dataPackage.scenicCandidates().size() > 1
                        ? dataPackage.scenicCandidates().get(1)
                        : morning;
        PoiCandidate lunch = first(dataPackage.foodCandidates());
        TransportRoute route = firstRoute(dataPackage.transportRoutes(), dayContext.hotelArea(), morning.name());

        List<TripPlanDTO.PlanItem> items =
                List.of(
                        new TripPlanDTO.PlanItem(
                                "09:00",
                                morning.name(),
                                "从住宿区域出发，游览工具候选中的核心景点。",
                                "2小时",
                                route.mode() + "，" + route.durationEstimate(),
                                null,
                                "地点来自 " + morning.source() + "，开放时间和门票建议出行前确认。"),
                        new TripPlanDTO.PlanItem(
                                "12:00",
                                lunch.name(),
                                "安排顺路午餐，减少中午跨区域移动。",
                                "1.5小时",
                                "步行或短途打车",
                                null,
                                "餐饮地点来自 " + lunch.source() + "，人均和营业时间以实际为准。"),
                        new TripPlanDTO.PlanItem(
                                "14:30",
                                afternoon.name(),
                                "下午继续在当天目标区域附近游览。",
                                "2小时",
                                "短途打车",
                                null,
                                "地点来自候选数据，不额外编造门票和预约规则。"));

        return new TripPlanDTO.DailyPlan(
                dayContext.day(),
                "第 " + dayContext.day() + " 天：" + dayContext.skeleton().theme(),
                context.getRequirement().destination(),
                context.getRequirement().destination(),
                context.getRequirement().destination(),
                null,
                null,
                items,
                List.of(lunch.name() + "：" + lunch.reason()),
                estimateDayCost(context),
                List.of("本日行程基于模拟工具候选生成；真实接入后只使用工具返回地点。"));
    }

    private Integer estimateDayCost(GenerateWorkflowContext context) {
        int peopleCount = context.getRequirement().peopleCount() == null ? 1 : context.getRequirement().peopleCount();
        return peopleCount * 180;
    }

    private PoiCandidate first(List<PoiCandidate> candidates) {
        return candidates.get(0);
    }

    private TransportRoute firstRoute(List<TransportRoute> routes, String from, String to) {
        if (!routes.isEmpty()) {
            return routes.get(0);
        }
        return new TransportRoute(from, to, "TAXI", "约 30 分钟", "约 8 公里", "SIMULATED_AMAP", true);
    }

    private DayContext findDayContext(GenerateWorkflowContext context, Integer day) {
        return context.getDayContexts().stream()
                .filter(item -> item.day().equals(day))
                .findFirst()
                .orElseThrow();
    }

    private List<String> names(List<PoiCandidate> candidates) {
        return candidates.stream().map(PoiCandidate::name).toList();
    }
}
