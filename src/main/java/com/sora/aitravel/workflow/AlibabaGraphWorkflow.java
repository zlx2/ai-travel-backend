package com.sora.aitravel.workflow;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategyFactoryBuilder;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Spring AI Alibaba Graph workflow adapter.
 *
 * <p>Business workflow classes build a {@link StateGraph} with this helper and execute it through
 * {@link CompiledGraph}. Node beans stay focused on domain work; orchestration is delegated to
 * Spring AI Alibaba instead of a local workflow interface.
 */
public final class AlibabaGraphWorkflow {

    private static final String CONTEXT_KEY = "workflowContext";

    private AlibabaGraphWorkflow() {}

    public static <C> Step<C> step(String name, WorkflowStep<C> action) {
        return new Step<>(name, action);
    }

    public static <C> CompiledGraph compile(String workflowName, List<Step<C>> steps) {
        Objects.requireNonNull(workflowName, "workflowName must not be null");
        Objects.requireNonNull(steps, "steps must not be null");
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("workflow steps must not be empty");
        }

        try {
            StateGraph graph =
                    new StateGraph(
                            workflowName,
                            new KeyStrategyFactoryBuilder()
                                    .addStrategy(CONTEXT_KEY, new ReplaceStrategy())
                                    .build());

            for (Step<C> step : steps) {
                graph.addNode(
                        step.getName(),
                        AsyncNodeAction.node_async(
                                state -> {
                                    C context = readContext(state);
                                    step.getAction().execute(context);
                                    return Map.of(CONTEXT_KEY, context);
                                }));
            }

            graph.addEdge(StateGraph.START, steps.get(0).getName());
            for (int i = 0; i < steps.size() - 1; i++) {
                graph.addEdge(steps.get(i).getName(), steps.get(i + 1).getName());
            }
            graph.addEdge(steps.get(steps.size() - 1).getName(), StateGraph.END);

            return graph.compile();
        } catch (GraphStateException ex) {
            throw new IllegalStateException(
                    "Failed to compile Spring AI Alibaba workflow: " + workflowName, ex);
        }
    }

    public static <C> C invoke(CompiledGraph graph, C context) {
        Objects.requireNonNull(graph, "graph must not be null");
        Objects.requireNonNull(context, "context must not be null");

        Map<String, Object> initialState = new LinkedHashMap<>();
        initialState.put(CONTEXT_KEY, context);
        return graph.invoke(initialState).map(AlibabaGraphWorkflow::<C>readContext).orElse(context);
    }

    @SuppressWarnings("unchecked")
    private static <C> C readContext(OverAllState state) {
        return (C)
                state.value(CONTEXT_KEY)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Spring AI Alibaba workflow state is missing "
                                                        + CONTEXT_KEY));
    }

    public static final class Step<C> {
        private final String name;
        private final WorkflowStep<C> action;

        private Step(String name, WorkflowStep<C> action) {
            Objects.requireNonNull(name, "step name must not be null");
            Objects.requireNonNull(action, "step action must not be null");
            this.name = name;
            this.action = action;
        }

        public String getName() {
            return name;
        }

        public WorkflowStep<C> getAction() {
            return action;
        }
    }

    @FunctionalInterface
    public interface WorkflowStep<C> {
        void execute(C context) throws Exception;
    }
}
