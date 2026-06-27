package com.sora.aitravel.workflow.analyze;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategyFactoryBuilder;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import jakarta.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 基于 Spring AI Alibaba Graph 的旅行 Analyze 工作流。 */
@Component
@RequiredArgsConstructor
public class TripAnalyzeWorkflow {

    private static final String CONTEXT = "context";
    private static final String USER_ID = "userId";
    private static final String REQUEST = "request";
    private static final String CLEAN_INPUT = "cleanInput";
    private static final String EXTRACTED_REQUIREMENT = "extractedRequirement";
    private static final String QUESTIONS = "questions";
    private static final String DESTINATION_SUGGESTIONS = "destinationSuggestions";
    private static final String CONFLICTS = "conflicts";
    private static final String STATUS = "status";
    private static final String RESULT = "result";

    private final UserRawInputNode userRawInputNode;
    private final InputPreprocessNode inputPreprocessNode;
    private final InfoExtractNode infoExtractNode;
    private final RequirementStandardizeNode requirementStandardizeNode;
    private final CompletenessCheckNode completenessCheckNode;
    private final ConflictCheckNode conflictCheckNode;
    private final AnalyzeResultMergeNode resultMergeNode;

    private CompiledGraph graph;

    @PostConstruct
    public void init() {
        this.graph =
                compile(
                        userRawInputNode,
                        inputPreprocessNode,
                        infoExtractNode,
                        requirementStandardizeNode,
                        completenessCheckNode,
                        conflictCheckNode,
                        resultMergeNode);
    }

    public AnalyzeWorkflowContext execute(AnalyzeWorkflowContext context) {
        return graph.invoke(toState(context)).map(TripAnalyzeWorkflow::readContext).orElse(context);
    }

    private CompiledGraph compile(
            UserRawInputNode userRawInputNode,
            InputPreprocessNode inputPreprocessNode,
            InfoExtractNode infoExtractNode,
            RequirementStandardizeNode requirementStandardizeNode,
            CompletenessCheckNode completenessCheckNode,
            ConflictCheckNode conflictCheckNode,
            AnalyzeResultMergeNode resultMergeNode) {
        try {
            StateGraph graph =
                    new StateGraph(
                            "trip-analyze-workflow",
                            new KeyStrategyFactoryBuilder()
                                    .addStrategy(CONTEXT, new ReplaceStrategy())
                                    .addStrategy(USER_ID, new ReplaceStrategy())
                                    .addStrategy(REQUEST, new ReplaceStrategy())
                                    .addStrategy(CLEAN_INPUT, new ReplaceStrategy())
                                    .addStrategy(EXTRACTED_REQUIREMENT, new ReplaceStrategy())
                                    .addStrategy(QUESTIONS, new ReplaceStrategy())
                                    .addStrategy(DESTINATION_SUGGESTIONS, new ReplaceStrategy())
                                    .addStrategy(CONFLICTS, new ReplaceStrategy())
                                    .addStrategy(STATUS, new ReplaceStrategy())
                                    .addStrategy(RESULT, new ReplaceStrategy())
                                    .build());

            graph.addNode("user-raw-input", node(userRawInputNode::execute));
            graph.addNode("input-preprocess", node(inputPreprocessNode::execute));
            graph.addNode("info-extract", node(infoExtractNode::execute));
            graph.addNode("requirement-standardize", node(requirementStandardizeNode::execute));
            graph.addNode("completeness-check", node(completenessCheckNode::execute));
            graph.addNode("conflict-check", node(conflictCheckNode::execute));
            graph.addNode("result-merge", node(resultMergeNode::execute));

            graph.addEdge(StateGraph.START, "user-raw-input");
            graph.addEdge("user-raw-input", "input-preprocess");
            graph.addEdge("input-preprocess", "info-extract");
            graph.addEdge("info-extract", "requirement-standardize");
            graph.addEdge("requirement-standardize", "completeness-check");
            graph.addConditionalEdges(
                    "completeness-check",
                    AsyncEdgeAction.edge_async(state -> state.value(STATUS, "NEED_MORE_INFO")),
                    Map.of("NEED_MORE_INFO", "result-merge", "READY", "conflict-check"));
            graph.addEdge("conflict-check", "result-merge");
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
                    return toState(context);
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

    private static Map<String, Object> toState(AnalyzeWorkflowContext context) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put(CONTEXT, context);
        state.put(USER_ID, context.getUserId());
        state.put(REQUEST, context.getRequest());
        state.put(CLEAN_INPUT, context.getCleanInput());
        state.put(EXTRACTED_REQUIREMENT, context.getExtractedRequirement());
        state.put(QUESTIONS, context.getQuestions());
        state.put(DESTINATION_SUGGESTIONS, context.getDestinationSuggestions());
        state.put(CONFLICTS, context.getConflicts());
        state.put(STATUS, context.getStatus());
        state.put(RESULT, context.getResult());
        return state;
    }

    @FunctionalInterface
    private interface NodeExecutor {
        void execute(AnalyzeWorkflowContext context) throws Exception;
    }
}
