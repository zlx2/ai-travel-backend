package com.sora.aitravel.workflow.analyze;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategyFactoryBuilder;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 基于 Spring AI Alibaba Graph 的旅行 Analyze 工作流。 */
@Component
public class TripAnalyzeWorkflow {

    private static final String CONTEXT = "context";

    private final CompiledGraph graph;

    public TripAnalyzeWorkflow(
            UserRawInputNode userRawInputNode,
            InputPreprocessNode inputPreprocessNode,
            InfoExtractNode infoExtractNode,
            RequirementStandardizeNode requirementStandardizeNode,
            CompletenessCheckNode completenessCheckNode,
            DestinationSuggestNode destinationSuggestNode,
            ConflictCheckNode conflictCheckNode,
            AnalyzeResultMergeNode resultMergeNode) {
        this.graph =
                compile(
                        userRawInputNode,
                        inputPreprocessNode,
                        infoExtractNode,
                        requirementStandardizeNode,
                        completenessCheckNode,
                        destinationSuggestNode,
                        conflictCheckNode,
                        resultMergeNode);
    }

    public AnalyzeWorkflowContext execute(AnalyzeWorkflowContext context) {
        Map<String, Object> initialState = new LinkedHashMap<>();
        initialState.put(CONTEXT, context);
        return graph.invoke(initialState).map(TripAnalyzeWorkflow::readContext).orElse(context);
    }

    private CompiledGraph compile(
            UserRawInputNode userRawInputNode,
            InputPreprocessNode inputPreprocessNode,
            InfoExtractNode infoExtractNode,
            RequirementStandardizeNode requirementStandardizeNode,
            CompletenessCheckNode completenessCheckNode,
            DestinationSuggestNode destinationSuggestNode,
            ConflictCheckNode conflictCheckNode,
            AnalyzeResultMergeNode resultMergeNode) {
        try {
            StateGraph graph =
                    new StateGraph(
                            "trip-analyze-workflow",
                            new KeyStrategyFactoryBuilder()
                                    .addStrategy(CONTEXT, new ReplaceStrategy())
                                    .build());

            graph.addNode("user-raw-input", node(userRawInputNode::execute));
            graph.addNode("input-preprocess", node(inputPreprocessNode::execute));
            graph.addNode("info-extract", node(infoExtractNode::execute));
            graph.addNode("requirement-standardize", node(requirementStandardizeNode::execute));
            graph.addNode("completeness-check", node(completenessCheckNode::execute));
            graph.addNode("destination-suggest", node(destinationSuggestNode::execute));
            graph.addNode("conflict-check", node(conflictCheckNode::execute));
            graph.addNode("result-merge", node(resultMergeNode::execute));

            graph.addEdge(StateGraph.START, "user-raw-input");
            graph.addEdge("user-raw-input", "input-preprocess");
            graph.addEdge("input-preprocess", "info-extract");
            graph.addEdge("info-extract", "requirement-standardize");
            graph.addEdge("requirement-standardize", "completeness-check");
            graph.addConditionalEdges(
                    "completeness-check",
                    AsyncEdgeAction.edge_async(state -> readContext(state).getStatus()),
                    Map.of(
                            "NEED_MORE_INFO",
                            "result-merge",
                            "NEED_DESTINATION_CHOICE",
                            "destination-suggest",
                            "READY",
                            "conflict-check"));
            graph.addEdge("destination-suggest", "result-merge");
            graph.addConditionalEdges(
                    "conflict-check",
                    AsyncEdgeAction.edge_async(state -> readContext(state).getStatus()),
                    Map.of("READY", "result-merge", "CONFLICT", "result-merge"));
            graph.addEdge("result-merge", StateGraph.END);
            return graph.compile();
        } catch (GraphStateException ex) {
            throw new IllegalStateException("Failed to compile trip analyze graph", ex);
        }
    }

    private AsyncNodeAction node(NodeExecutor executor) {
        return AsyncNodeAction.node_async(
                state -> {
                    AnalyzeWorkflowContext context = readContext(state);
                    executor.execute(context);
                    return Map.of(CONTEXT, context);
                });
    }

    private static AnalyzeWorkflowContext readContext(OverAllState state) {
        return (AnalyzeWorkflowContext)
                state.value(CONTEXT)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Spring AI Alibaba Graph state is missing "
                                                        + CONTEXT));
    }

    @FunctionalInterface
    private interface NodeExecutor {
        void execute(AnalyzeWorkflowContext context) throws Exception;
    }
}
