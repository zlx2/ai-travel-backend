package com.sora.aitravel.workflow.analyze;

import com.sora.aitravel.common.enums.AnalyzeStatusEnum;
import com.sora.aitravel.dto.model.ConflictDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 信息完整时，用规则判断需求是否存在明显冲突。 */
@Slf4j
@Component
public class ConflictCheckNode {

    public void execute(AnalyzeWorkflowContext context) {
        TravelRequirementDTO requirement = context.getExtractedRequirement();
        List<ConflictDTO> conflicts = new ArrayList<>();

        if (requirement == null) {
            context.setConflicts(List.of());
            context.setStatus(AnalyzeStatusEnum.READY.name());
            return;
        }

        checkDays(requirement, conflicts);
        checkPeopleCount(requirement, conflicts);
        checkBudget(requirement, conflicts);
        checkTravelDate(requirement, conflicts);
        checkRoute(requirement, conflicts);
        checkRental(requirement, conflicts);

        context.setConflicts(conflicts);
        context.setStatus(
                conflicts.isEmpty()
                        ? AnalyzeStatusEnum.READY.name()
                        : AnalyzeStatusEnum.CONFLICT.name());
        log.info("节点[conflict-check]：规则冲突检查完成，conflictCount={}", conflicts.size());
    }

    private void checkDays(TravelRequirementDTO requirement, List<ConflictDTO> conflicts) {
        Integer days = requirement.getDays();
        if (days == null) {
            return;
        }
        if (days < 1) {
            conflicts.add(new ConflictDTO("DAYS_TOO_SHORT", "旅行天数不能小于 1 天。", "请把旅行天数调整为 1-7 天。"));
        } else if (days > 7) {
            conflicts.add(
                    new ConflictDTO(
                            "DAYS_TOO_LONG", "当前 Analyze V1 只支持 1-7 天的普通旅行规划。", "建议缩短行程天数。"));
        }
    }

    private void checkBudget(TravelRequirementDTO requirement, List<ConflictDTO> conflicts) {
        Integer budget = requirement.getBudget();
        if (budget == null) {
            return;
        }
        int peopleCount = requirement.getPeopleCount() == null ? 1 : requirement.getPeopleCount();
        int days = requirement.getDays() == null ? 1 : Math.max(requirement.getDays(), 1);
        int perPersonPerDay = budget / Math.max(peopleCount * days, 1);
        if (budget <= 0) {
            conflicts.add(new ConflictDTO("BUDGET_INVALID", "预算金额需要大于 0。", "请补充一个可用的旅行预算。"));
        } else if (perPersonPerDay < 100) {
            conflicts.add(
                    new ConflictDTO(
                            "BUDGET_TOO_LOW",
                            "%d 个人玩 %d 天预算 %d 元明显偏低，可能无法覆盖基础交通和餐饮。"
                                    .formatted(peopleCount, days, budget),
                            "建议提高预算，或缩短行程天数。"));
        }
    }

    private void checkPeopleCount(TravelRequirementDTO requirement, List<ConflictDTO> conflicts) {
        Integer peopleCount = requirement.getPeopleCount();
        if (peopleCount == null) {
            return;
        }
        if (peopleCount < 1) {
            conflicts.add(new ConflictDTO("PEOPLE_COUNT_INVALID", "出行人数不能小于 1。", "请确认实际出行人数。"));
        } else if (peopleCount > 20) {
            conflicts.add(
                    new ConflictDTO(
                            "PEOPLE_COUNT_TOO_LARGE",
                            "当前需求人数较多，普通自由行规划可能无法覆盖团队接待细节。",
                            "建议拆分小组，或补充团队出行的车辆、住宿和集合要求。"));
        }
    }

    private void checkTravelDate(TravelRequirementDTO requirement, List<ConflictDTO> conflicts) {
        String travelDate = requirement.getTravelDate();
        if (travelDate == null || travelDate.isBlank()) {
            return;
        }
        try {
            LocalDate date = LocalDate.parse(travelDate);
            if (date.isBefore(LocalDate.now())) {
                conflicts.add(
                        new ConflictDTO(
                                "TRAVEL_DATE_PAST",
                                "出行日期已经过去，无法按该日期生成未来行程。",
                                "请重新选择一个今天或之后的出行日期。"));
            }
        } catch (DateTimeParseException ignored) {
            conflicts.add(
                    new ConflictDTO(
                            "TRAVEL_DATE_INVALID", "出行日期格式无法识别。", "请使用 yyyy-MM-dd 格式补充出行日期。"));
        }
    }

    private void checkRoute(TravelRequirementDTO requirement, List<ConflictDTO> conflicts) {
        List<String> routeCities = requirement.getRouteCities();
        if ("ROAD_TRIP".equals(requirement.getRouteMode())
                && routeCities != null
                && routeCities.isEmpty()
                && isBlank(requirement.getRouteRegion())
                && isBlank(requirement.getDestination())) {
            conflicts.add(
                    new ConflictDTO(
                            "ROAD_TRIP_TARGET_MISSING",
                            "自驾路线缺少目标城市、途经城市或路线区域。",
                            "请至少补充一个目标城市或自驾区域。"));
        }
    }

    private void checkRental(TravelRequirementDTO requirement, List<ConflictDTO> conflicts) {
        boolean rentalRequired =
                "USER_REQUIRED".equals(requirement.getRentalIntent())
                        || (requirement.getRentalRequirement() != null
                                && Boolean.TRUE.equals(
                                        requirement.getRentalRequirement().getNeedRental()));
        if (rentalRequired && "NO_RENTAL".equals(requirement.getRentalIntent())) {
            conflicts.add(
                    new ConflictDTO(
                        "RENTAL_TRANSPORT_CONFLICT",
                            "用户同时选择了租车和不租车。",
                            "请确认本次是否进入租车行程。"));
        }
        if (requirement.getRentalRequirement() != null
                && requirement.getRentalRequirement().getRentalDays() != null
                && requirement.getDays() != null
                && requirement.getRentalRequirement().getRentalDays() > requirement.getDays()) {
            conflicts.add(
                    new ConflictDTO("RENTAL_DAYS_CONFLICT", "租车天数大于旅行总天数。", "请调整租车天数，或确认旅行总天数。"));
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
