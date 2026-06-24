package com.sora.aitravel.workflow.generate;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 初始化逐日生成状态。 */
@Slf4j
@Component
public class DayStateInitNode {

    public void execute(GenerateWorkflowContext context) {
        context.setDayContexts(List.of());
        context.setDayQueryPlans(List.of());
        context.setRankedDayDataPackages(List.of());
        context.setDayValidationReports(List.of());
        context.setLockedDailyPlans(new ArrayList<>());
        log.info("节点[day-state-init]：初始化逐日生成状态，days={}", context.getRequirement().days());
    }
}
