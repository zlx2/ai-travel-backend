package com.sora.aitravel.workflow.generate.node.day;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.sora.aitravel.workflow.generate.state.TripGraphStateCodec;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Finalizes the generated day plan by assembling and validating the timeline. */
@Component
@RequiredArgsConstructor
public class DayPlanFinalizeNode {

    private final TripTimelineAssembler tripTimelineAssembler;
    private final DayPlanValidateNode dayPlanValidateNode;

    public Map<String, Object> execute(OverAllState state) {
        Map<String, Object> patch = new LinkedHashMap<>(tripTimelineAssembler.execute(state));
        patch.putAll(dayPlanValidateNode.execute(TripGraphStateCodec.withPatch(state, patch)));
        return patch;
    }
}
