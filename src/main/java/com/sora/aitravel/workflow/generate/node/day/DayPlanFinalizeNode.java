package com.sora.aitravel.workflow.generate.node.day;

import com.alibaba.cloud.ai.graph.OverAllState;
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
        patch.putAll(dayPlanValidateNode.execute(stateWithPatch(state, patch)));
        return patch;
    }

    private OverAllState stateWithPatch(OverAllState state, Map<String, Object> patch) {
        Map<String, Object> data = new LinkedHashMap<>(state.data());
        data.putAll(patch);
        return new OverAllState(data);
    }
}
