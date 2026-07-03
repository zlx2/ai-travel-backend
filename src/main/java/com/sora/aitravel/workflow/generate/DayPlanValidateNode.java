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
import com.sora.aitravel.service.route.GeoRouteCalculator;
import com.sora.aitravel.service.impl.PoiIdentityServiceImpl;
import com.sora.aitravel.service.impl.RouteShapeValidatorImpl;
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
    private static final double MIN_SAME_DAY_SPOT_DISTANCE_KM = 1.2;
    private static final String TYPE_LUNCH_AREA = "LUNCH_AREA";
    private static final String TYPE_DINNER_AREA = "DINNER_AREA";
    private static final Set<String> UTILITY_TIMELINE_TYPES =
            Set.of(
                    "DAY_START",
                    "TRANSFER",
                    "RENTAL_PICKUP",
                    "CAR_RETURN_SERVICE",
                    TYPE_LUNCH_AREA,
                    TYPE_DINNER_AREA,
                    "STAY_AREA");

    private final RouteShapeValidatorImpl routeShapeValidator;
    private final PoiIdentityServiceImpl poiIdentityService;

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
        return report.getWarnings() != null && !report.getWarnings().isEmpty();
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
            if (isInternalFacility(spot.getName())) {
                warnings.add("景点疑似景区内部设施：" + spot.getName());
            }
        }
        warnings.addAll(validateSpotSpacing(spots));
        warnings.addAll(validateTimelineOrder(dailyPlan, spots));
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

    private List<String> validateTimelineOrder(
            TripPlanDTO.DailyPlan dailyPlan, List<TripPlanDTO.Spot> spots) {
        List<String> warnings = new ArrayList<>();
        List<TripPlanDTO.TimelineNode> timeline =
                dailyPlan.getTimeline() == null ? List.of() : dailyPlan.getTimeline();
        if (timeline.isEmpty()) {
            warnings.add("时间线为空");
            return warnings;
        }
        int previousStart = -1;
        for (TripPlanDTO.TimelineNode node : timeline) {
            int start = parseTime(node.getStartTime());
            if (start < 0) {
                warnings.add("时间线节点缺少有效时间：" + node.getTitle());
                continue;
            }
            if (previousStart > start) {
                warnings.add("时间线顺序错误：" + node.getTitle());
            }
            previousStart = Math.max(previousStart, start);
        }

        long scenicTimelineCount =
                timeline.stream().filter(node -> !isUtilityTimelineType(node.getType())).count();
        if (scenicTimelineCount < spots.size()) {
            warnings.add("时间线景点数量少于当天景点数量");
        }

        int lunchIndex = firstTimelineIndex(timeline, TYPE_LUNCH_AREA);
        int dinnerIndex = firstTimelineIndex(timeline, TYPE_DINNER_AREA);
        if (lunchIndex >= 0 && dinnerIndex >= 0) {
            if (dinnerIndex <= lunchIndex) {
                warnings.add("午餐和晚餐顺序错误");
            } else if (spots.size() >= 3
                    && timeline.subList(lunchIndex + 1, dinnerIndex).stream()
                            .noneMatch(node -> !isUtilityTimelineType(node.getType()))) {
                warnings.add("午餐和晚餐之间缺少游览节点");
            }
        }
        return warnings;
    }

    private boolean isUtilityTimelineType(String type) {
        return type != null && UTILITY_TIMELINE_TYPES.contains(type);
    }

    private int firstTimelineIndex(List<TripPlanDTO.TimelineNode> timeline, String type) {
        for (int index = 0; index < timeline.size(); index++) {
            if (type.equals(timeline.get(index).getType())) {
                return index;
            }
        }
        return -1;
    }

    private int parseTime(String time) {
        if (time == null || !time.matches("\\d{2}:\\d{2}")) {
            return -1;
        }
        String[] parts = time.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }

    private List<String> validateSpotSpacing(List<TripPlanDTO.Spot> spots) {
        List<String> warnings = new ArrayList<>();
        for (int i = 0; i < spots.size(); i++) {
            for (int j = i + 1; j < spots.size(); j++) {
                TripPlanDTO.Spot first = spots.get(i);
                TripPlanDTO.Spot second = spots.get(j);
                if (first.getLng() == null
                        || first.getLat() == null
                        || second.getLng() == null
                        || second.getLat() == null) {
                    continue;
                }
                double distanceKm =
                        GeoRouteCalculator.distanceKm(
                                first.getLng().doubleValue(),
                                first.getLat().doubleValue(),
                                second.getLng().doubleValue(),
                                second.getLat().doubleValue());
                if (distanceKm < MIN_SAME_DAY_SPOT_DISTANCE_KM) {
                    warnings.add(
                            "当天景点点位过近："
                                    + first.getName()
                                    + " / "
                                    + second.getName()
                                    + "，约 "
                                    + String.format("%.1f", distanceKm)
                                    + " km");
                }
            }
        }
        return warnings;
    }

    private boolean isInternalFacility(String name) {
        String text = name == null ? "" : name;
        return containsAny(
                text,
                "监控室",
                "值班室",
                "办公室",
                "警务室",
                "派出所",
                "管理房",
                "工作站",
                "收费站",
                "管理处",
                "管理局",
                "管委会",
                "综合执法",
                "指挥部",
                "执勤点",
                "检查站");
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
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
