package com.sora.aitravel.workflow.chat;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategyFactoryBuilder;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import jakarta.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiChatWorkflow {

    private static final String CONTEXT = "context";
    private static final String USER_ID = "userId";
    private static final String REQUEST = "request";
    private static final String TRIP_PLAN_JSON = "tripPlanJson";
    private static final String PROMPT = "prompt";
    private static final String RAW_MODEL_RESPONSE = "rawModelResponse";
    private static final String RESULT = "result";

    private final ChatContextLoadNode contextLoadNode;
    private final TripContextPrepareNode tripContextPrepareNode;
    private final ChatPromptBuildNode promptBuildNode;
    private final ModelCallNode modelCallNode;
    private final ChatResultFormatNode resultFormatNode;

    private CompiledGraph graph;

    @PostConstruct
    public void init() {
        this.graph =
                compile(
                        contextLoadNode,
                        tripContextPrepareNode,
                        promptBuildNode,
                        modelCallNode,
                        resultFormatNode);
    }

    public ChatWorkflowContext execute(ChatWorkflowContext context) {
        return graph.invoke(toState(context)).map(AiChatWorkflow::readContext).orElse(context);
    }

    private CompiledGraph compile(
            ChatContextLoadNode contextLoadNode,
            TripContextPrepareNode tripContextPrepareNode,
            ChatPromptBuildNode promptBuildNode,
            ModelCallNode modelCallNode,
            ChatResultFormatNode resultFormatNode) {
        try {
            StateGraph graph =
                    new StateGraph(
                            "ai-chat-workflow",
                            new KeyStrategyFactoryBuilder()
                                    .addStrategy(CONTEXT, new ReplaceStrategy())
                                    .addStrategy(USER_ID, new ReplaceStrategy())
                                    .addStrategy(REQUEST, new ReplaceStrategy())
                                    .addStrategy(TRIP_PLAN_JSON, new ReplaceStrategy())
                                    .addStrategy(PROMPT, new ReplaceStrategy())
                                    .addStrategy(RAW_MODEL_RESPONSE, new ReplaceStrategy())
                                    .addStrategy(RESULT, new ReplaceStrategy())
                                    .build());

            graph.addNode("context-load", node(contextLoadNode::execute));
            graph.addNode("trip-context-prepare", node(tripContextPrepareNode::execute));
            graph.addNode("prompt-build", node(promptBuildNode::execute));
            graph.addNode("model-call", node(modelCallNode::execute));
            graph.addNode("result-format", node(resultFormatNode::execute));

            graph.addEdge(StateGraph.START, "context-load");
            graph.addEdge("context-load", "trip-context-prepare");
            graph.addEdge("trip-context-prepare", "prompt-build");
            graph.addEdge("prompt-build", "model-call");
            graph.addEdge("model-call", "result-format");
            graph.addEdge("result-format", StateGraph.END);
            return graph.compile();
        } catch (GraphStateException ex) {
            throw new IllegalStateException("Failed to compile ai chat graph", ex);
        }
    }

    private AsyncNodeAction node(NodeExecutor executor) {
        return AsyncNodeAction.node_async(
                state -> {
                    ChatWorkflowContext context = readContext(state);
                    executor.execute(context);
                    return toState(context);
                });
    }

    private static ChatWorkflowContext readContext(OverAllState state) {
        return (ChatWorkflowContext)
                state.value(CONTEXT)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Spring AI Alibaba Graph state is missing "
                                                        + CONTEXT));
    }

    private static Map<String, Object> toState(ChatWorkflowContext context) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put(CONTEXT, context);
        state.put(USER_ID, context.getUserId());
        state.put(REQUEST, context.getRequest());
        state.put(TRIP_PLAN_JSON, context.getTripPlanJson());
        state.put(PROMPT, context.getPrompt());
        state.put(RAW_MODEL_RESPONSE, context.getRawModelResponse());
        state.put(RESULT, context.getResult());
        return state;
    }

    @FunctionalInterface
    private interface NodeExecutor {
        void execute(ChatWorkflowContext context) throws Exception;
    }
}
