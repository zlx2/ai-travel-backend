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
 * Spring AI Alibaba Graph 工作流适配器。
 *
 * <p>业务工作流类通过此工具类构建 {@link StateGraph} 并用 {@link CompiledGraph} 执行。
 * 各节点只需关注领域逻辑，编排委托给 Spring AI Alibaba 的状态图引擎，而非自行实现工作流接口。
 *
 * <p>使用方式：
 * <pre>{@code
 * CompiledGraph graph = AlibabaGraphWorkflow.compile("myWorkflow", List.of(
 *     AlibabaGraphWorkflow.step("step1", ctx -> { ... }),
 *     AlibabaGraphWorkflow.step("step2", ctx -> { ... })
 * ));
 * MyContext result = AlibabaGraphWorkflow.invoke(graph, myContext);
 * }</pre>
 */
public final class AlibabaGraphWorkflow {

    /** State 中上下文对象的存储键，通过 ReplaceStrategy 每次覆盖旧值。 */
    private static final String CONTEXT_KEY = "workflowContext";

    private AlibabaGraphWorkflow() {}

    /**
     * 创建一个工作流步骤。
     *
     * @param name   步骤名称，作为图节点标识
     * @param action 步骤执行的业务逻辑
     * @param <C>    上下文类型
     */
    public static <C> Step<C> step(String name, WorkflowStep<C> action) {
        return new Step<>(name, action);
    }

    /**
     * 编译步骤列表为可执行的有向无环图。
     *
     * <p>步骤按传入顺序串联：START → step1 → step2 → ... → stepN → END。
     * 每一步从 state 中读取上下文、执行业务逻辑、再将更新后的上下文写回 state。
     *
     * @param workflowName 工作流名称，用于图标识
     * @param steps        有序步骤列表（不能为空）
     * @param <C>          上下文类型
     * @return 编译后的可执行图
     * @throws IllegalArgumentException steps 为空时抛出
     * @throws IllegalStateException    图编译失败时抛出（如步骤名重复）
     */
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

    /**
     * 执行编译好的工作流图并返回最终上下文。
     *
     * <p>将上下文包装为初始 state 传入图引擎，执行完成后从 state 中提取并返回最终上下文。
     *
     * @param graph   编译好的工作流图
     * @param context 初始上下文
     * @param <C>     上下文类型
     * @return 执行结束后的上下文
     */
    public static <C> C invoke(CompiledGraph graph, C context) {
        Objects.requireNonNull(graph, "graph must not be null");
        Objects.requireNonNull(context, "context must not be null");

        Map<String, Object> initialState = new LinkedHashMap<>();
        initialState.put(CONTEXT_KEY, context);
        return graph.invoke(initialState).map(AlibabaGraphWorkflow::<C>readContext).orElse(context);
    }

    /**
     * 从 OverAllState 中读取上下文对象。
     *
     * @param state Spring AI Alibaba Graph 的全局状态
     * @param <C>   上下文类型
     * @return 提取的上下文
     * @throws IllegalStateException state 中缺少上下文键时抛出
     */
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

    /**
     * 工作流步骤的定义。每个步骤包含名称和执行业务逻辑的动作。
     *
     * @param <C> 上下文类型
     */
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

    /**
     * 工作流步骤的业务逻辑接口。
     *
     * @param <C> 上下文类型
     */
    @FunctionalInterface
    public interface WorkflowStep<C> {
        void execute(C context) throws Exception;
    }
}
