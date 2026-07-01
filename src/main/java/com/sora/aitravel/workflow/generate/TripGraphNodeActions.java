package com.sora.aitravel.workflow.generate;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.sora.aitravel.common.utils.WorkflowTiming;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared Spring AI Alibaba Graph node adapters for trip generation workflows. */
public final class TripGraphNodeActions {
    private static final Logger TIMING_LOGGER = LoggerFactory.getLogger(WorkflowTiming.class);

    private TripGraphNodeActions() {}

    public static AsyncNodeAction stateNode(
            String workflowName, String nodeName, StateNodeExecutor executor) {
        return AsyncNodeAction.node_async(
                state -> {
                    long start = WorkflowTiming.start();
                    try {
                        return executor.execute(state);
                    } finally {
                        TIMING_LOGGER.info(
                                "行程生成耗时 workflow={} node={} elapsedMs={}",
                                workflowName,
                                nodeName,
                                WorkflowTiming.elapsedMs(start));
                    }
                });
    }

    @FunctionalInterface
    public interface StateNodeExecutor {
        Map<String, Object> execute(OverAllState state) throws Exception;
    }
}
