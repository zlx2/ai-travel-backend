package com.sora.aitravel.workflow.generate;

import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.DAY_VALIDATION_REPORTS;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.LOCKED_DAILY_PLANS;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.RANKED_DAY_DATA_PACKAGES;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.REQUIREMENT;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.SELECTED_QUOTE;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.SINGLE_DAY_GENERATION;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.dto.model.RentalQuoteOptionDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.model.TripPlanDTO;
import com.sora.aitravel.model.DayDataPackage;
import com.sora.aitravel.model.DayPlanValidationReport;
import com.sora.aitravel.service.PoiIdentityService;
import com.sora.aitravel.service.RouteShapeValidator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 校验每天行程是否基于候选 POI，且满足前端地图和卡片展示所需字段。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DayPlanValidateNode {
    private final RouteShapeValidator routeShapeValidator;
    private final PoiIdentityService poiIdentityService;

    public Map<String, Object> execute(OverAllState state) {
        List<DayPlanValidationReport> reports =
                validatePlans(
                        TripGraphStateCodec.optionalList(
                                state, LOCKED_DAILY_PLANS, TripPlanDTO.DailyPlan.class),
                        TripGraphStateCodec.optionalList(
                                state, RANKED_DAY_DATA_PACKAGES, DayDataPackage.class),
                        TripGraphStateCodec.required(
                                state, REQUIREMENT, TravelRequirementDTO.class),
                        TripGraphStateCodec.optional(
                                        state, SELECTED_QUOTE, RentalQuoteOptionDTO.class)
                                .orElse(null),
                        TripGraphStateCodec.optional(state, SINGLE_DAY_GENERATION, Boolean.class)
                                .orElse(false));
        return TripGraphStateCodec.patch(DAY_VALIDATION_REPORTS, reports);
    }

    private List<DayPlanValidationReport> validatePlans(
            List<TripPlanDTO.DailyPlan> lockedDailyPlans,
            List<DayDataPackage> rankedDayDataPackages,
            TravelRequirementDTO requirement,
            RentalQuoteOptionDTO selectedQuote,
            boolean singleDayGeneration) {
        List<DayPlanValidationReport> reports = new ArrayList<>();
        Set<String> usedSpotNames = new HashSet<>();
        for (TripPlanDTO.DailyPlan dailyPlan : lockedDailyPlans) {
            DayDataPackage dataPackage = findDataPackage(rankedDayDataPackages, dailyPlan.getDay());
            List<String> warnings =
                    validateDay(dailyPlan, dataPackage, usedSpotNames, selectedQuote != null);
            reports.add(
                    new DayPlanValidationReport(dailyPlan.getDay(), warnings.isEmpty(), warnings));
            log.info(
                    "节点[day-plan-finalize]：第 {} 天校验完成，passed={}, warnings={}",
                    dailyPlan.getDay(),
                    warnings.isEmpty(),
                    warnings);
        }
        List<DayPlanValidationReport> failed =
                reports.stream()
                        .filter(report -> !Boolean.TRUE.equals(report.getPassed()))
                        .toList();
        List<DayPlanValidationReport> blockingFailures =
                failed.stream().filter(this::isBlockingFailure).toList();
        boolean dayCountMismatch =
                !singleDayGeneration && lockedDailyPlans.size() != requirement.getDays();
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
        return reports;
    }

    private boolean isBlockingFailure(DayPlanValidationReport report) {
        return report.getWarnings().stream()
                .anyMatch(
                        warning ->
                                !warning.startsWith("每天应安排 2-4 个景点")
                                        && !warning.startsWith("景点在多天行程中重复")
                                        && !warning.startsWith("景点不在候选 POI 中")
                                        && !warning.startsWith("景点缺少经纬度")
                                        && !warning.startsWith("租车行程存在非自驾路线段")
                                        && !warning.startsWith("当天存在过长单段路线")
                                        && !warning.startsWith("当天路线总距离过长")
                                        && !warning.startsWith("前端地图路线顺序明显绕路")
                                        && !warning.startsWith("前端地图路线总距离过长")
                                        && !warning.startsWith("当天路线存在明显折返"));
    }

    private List<String> validateDay(
            TripPlanDTO.DailyPlan dailyPlan,
            DayDataPackage dataPackage,
            Set<String> usedSpotNames,
            boolean rentalEnabled) {
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
                            normalizedAllowedNames.add(
                                    poiIdentityService.normalizeName(item.getName()));
                        });

        for (TripPlanDTO.Spot spot : spots) {
            String normalizedName = poiIdentityService.normalizeName(spot.getName());
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
        if (rentalEnabled && dailyPlan.getRouteLegs() != null) {
            boolean hasNonDriving =
                    dailyPlan.getRouteLegs().stream()
                            .anyMatch(
                                    leg ->
                                            leg.getMode() != null
                                                    && !"DRIVING".equals(leg.getMode())
                                                    && !"UNKNOWN".equals(leg.getMode()));
            if (hasNonDriving) {
                warnings.add("租车行程存在非自驾路线段");
            }
        }
        warnings.addAll(routeShapeValidator.validate(dailyPlan, rentalEnabled));
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

    private DayDataPackage findDataPackage(
            List<DayDataPackage> rankedDayDataPackages, Integer day) {
        return rankedDayDataPackages.stream()
                .filter(item -> item.getDay().equals(day))
                .findFirst()
                .orElseThrow();
    }
}
