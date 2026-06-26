package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.dto.model.TripPlanDTO;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 校验每天行程是否基于候选 POI，且满足前端地图和卡片展示所需字段。 */
@Slf4j
@Component
public class DayPlanValidateNode {

    public void execute(GenerateWorkflowContext context) {
        List<DayPlanValidationReport> reports = new ArrayList<>();
        Set<String> usedSpotNames = new HashSet<>();
        for (TripPlanDTO.DailyPlan dailyPlan : context.getLockedDailyPlans()) {
            DayDataPackage dataPackage = findDataPackage(context, dailyPlan.getDay());
            List<String> warnings = validateDay(dailyPlan, dataPackage, usedSpotNames);
            reports.add(
                    new DayPlanValidationReport(dailyPlan.getDay(), warnings.isEmpty(), warnings));
            log.info(
                    "节点[day-plan-validate]：第 {} 天校验完成，passed={}, warnings={}",
                    dailyPlan.getDay(),
                    warnings.isEmpty(),
                    warnings);
        }
        context.setDayValidationReports(reports);
        List<DayPlanValidationReport> failed =
                reports.stream()
                        .filter(report -> !Boolean.TRUE.equals(report.getPassed()))
                        .toList();
        List<DayPlanValidationReport> blockingFailures =
                failed.stream().filter(this::isBlockingFailure).toList();
        boolean dayCountMismatch =
                !context.isSingleDayGeneration()
                        && context.getLockedDailyPlans().size() != context.getRequirement().getDays();
        if (blockingFailures.size() > 0 || dayCountMismatch) {
            String details =
                    blockingFailures.stream()
                            .map(
                                    report ->
                                            "第 "
                                                    + report.getDay()
                                                    + " 天："
                                                    + String.join("；", report.getWarnings()))
                            .reduce((left, right) -> left + "；" + right)
                            .orElse("返回天数与需求不一致");
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "行程数据不完整，请重新生成：" + details);
        }
    }

    private boolean isBlockingFailure(DayPlanValidationReport report) {
        return report.getWarnings().stream()
                .anyMatch(
                        warning ->
                                !warning.startsWith("每天应安排 2-4 个景点")
                                        && !warning.startsWith("景点在多天行程中重复")
                                        && !warning.startsWith("景点不在候选 POI 中")
                                        && !warning.startsWith("景点缺少经纬度"));
    }

    private List<String> validateDay(
            TripPlanDTO.DailyPlan dailyPlan,
            DayDataPackage dataPackage,
            Set<String> usedSpotNames) {
        List<String> warnings = new ArrayList<>();
        List<TripPlanDTO.Spot> spots =
                dailyPlan.getSpots() == null ? List.of() : dailyPlan.getSpots();
        if (spots.size() < 2 || spots.size() > 4) {
            warnings.add("每天应安排 2-4 个景点，当前为 " + spots.size());
        }
        if (dailyPlan.getIntensity() == null || dailyPlan.getIntensity().isBlank()) {
            warnings.add("缺少当天强度 intensity");
        }

        Set<String> allowedPoiIds = new HashSet<>();
        Set<String> allowedNames = new HashSet<>();
        Set<String> normalizedAllowedNames = new HashSet<>();
        dataPackage
                .scenicCandidates()
                .forEach(
                        item -> {
                            allowedPoiIds.add(item.getSourcePoiId());
                            allowedNames.add(item.getName());
                            normalizedAllowedNames.add(normalizePoiName(item.getName()));
                        });

        for (TripPlanDTO.Spot spot : spots) {
            String normalizedName = normalizePoiName(spot.getName());
            if (!allowedPoiIds.contains(spot.getPoiId())
                    && !allowedNames.contains(spot.getName())
                    && !normalizedAllowedNames.contains(normalizedName)
                    && !hasSimilarAllowedName(normalizedName, normalizedAllowedNames)) {
                warnings.add("景点不在候选 POI 中：" + spot.getName());
            }
            if (spot.getLng() == null || spot.getLat() == null) {
                warnings.add("景点缺少经纬度：" + spot.getName());
            }
            if (spot.getOrder() == null) {
                warnings.add("景点缺少排序 order：" + spot.getName());
            }
            if (!normalizedName.isBlank() && !usedSpotNames.add(normalizedName)) {
                warnings.add("景点在多天行程中重复：" + spot.getName());
            }
        }
        return warnings;
    }

    private boolean hasSimilarAllowedName(String spotName, Set<String> allowedNames) {
        if (spotName == null || spotName.isBlank()) {
            return false;
        }
        return allowedNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .anyMatch(name -> name.contains(spotName) || spotName.contains(name));
    }

    private String normalizePoiName(String name) {
        if (name == null) {
            return "";
        }
        return name.replaceAll("[（(].*?[）)]", "")
                .replaceAll("[-—·].*$", "")
                .replace("景区", "")
                .replace("风景区", "")
                .replace("步行街", "")
                .replaceAll("\\s+", "")
                .trim();
    }

    private DayDataPackage findDataPackage(GenerateWorkflowContext context, Integer day) {
        return context.getRankedDayDataPackages().stream()
                .filter(item -> item.getDay().equals(day))
                .findFirst()
                .orElseThrow();
    }
}
