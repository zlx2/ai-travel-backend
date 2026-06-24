package com.sora.aitravel.workflow.generate;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 构建每一天生成行程所需的上下文。 */
@Slf4j
@Component
public class DayContextBuildNode {

    public void execute(GenerateWorkflowContext context) {
        List<DayContext> dayContexts = new ArrayList<>();
        List<String> usedPlaces = new ArrayList<>();
        String hotelArea = context.getCityProfile().hotelCandidates().get(0).getName();

        for (DaySkeleton skeleton : context.getDaySkeletons()) {
            dayContexts.add(
                    new DayContext(
                            skeleton.getDay(),
                            skeleton,
                            List.copyOf(usedPlaces),
                            hotelArea,
                            context.getRequirement().getPace()));
            usedPlaces.add(skeleton.targetArea());
        }

        context.setDayContexts(dayContexts);
        log.info("节点[day-context-build]：已构建每天上下文，count={}", dayContexts.size());
    }
}
