package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.dto.model.TripPlanDTO;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 校验每天行程是否基于候选数据、路线是否过重、体验是否符合节奏。 */
@Slf4j
@Component
public class DayPlanValidateNode {

    public void execute(GenerateWorkflowContext context) {
        List<DayPlanValidationReport> reports = new ArrayList<>();
        for (TripPlanDTO.DailyPlan dailyPlan : context.getLockedDailyPlans()) {
            DayDataPackage dataPackage = findDataPackage(context, dailyPlan.getDay());
            List<String> warnings = validateDay(context, dailyPlan, dataPackage);
            reports.add(
                    new DayPlanValidationReport(dailyPlan.getDay(), warnings.isEmpty(), warnings));
            log.info(
                    "节点[day-plan-validate]：第 {} 天校验完成，passed={}, warnings={}",
                    dailyPlan.getDay(),
                    warnings.isEmpty(),
                    warnings);
        }
        context.setDayValidationReports(reports);
    }

    private List<String> validateDay(
            GenerateWorkflowContext context,
            TripPlanDTO.DailyPlan dailyPlan,
            DayDataPackage dataPackage) {
        List<String> warnings = new ArrayList<>();
        Set<String> allowedPlaces = new HashSet<>();
        dataPackage.scenicCandidates().forEach(item -> allowedPlaces.add(item.getName()));
        dataPackage.foodCandidates().forEach(item -> allowedPlaces.add(item.getName()));

        for (TripPlanDTO.PlanItem item : dailyPlan.getItems()) {
            if (!allowedPlaces.contains(item.getPlace())) {
                warnings.add("地点不在工具候选中：" + item.getPlace());
            }
        }
        if ("LIGHT".equals(context.getRequirement().getPace()) && dailyPlan.getItems().size() > 4) {
            warnings.add("轻松节奏下当天安排偏多。");
        }
        if (dataPackage.transportRoutes().isEmpty()) {
            warnings.add("当天缺少交通路线数据，仅能按估算展示。");
        }
        return warnings;
    }

    private DayDataPackage findDataPackage(GenerateWorkflowContext context, Integer day) {
        return context.getRankedDayDataPackages().stream()
                .filter(item -> item.getDay().equals(day))
                .findFirst()
                .orElseThrow();
    }
}
