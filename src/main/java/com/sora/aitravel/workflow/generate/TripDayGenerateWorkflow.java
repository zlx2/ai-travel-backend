package com.sora.aitravel.workflow.generate;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategyFactoryBuilder;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.dto.model.TripPlanDTO;
import jakarta.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 基于 Spring AI Alibaba Graph 的单日行程生成工作流。 */
@Component
@RequiredArgsConstructor
public class TripDayGenerateWorkflow {

    private static final String CONTEXT = "context";
    private static final String DAY_CONTEXTS = "dayContexts";
    private static final String DAY_QUERY_PLANS = "dayQueryPlans";
    private static final String LOCKED_DAILY_PLANS = "lockedDailyPlans";

    private final DayContextBuildNode dayContextBuildNode;
    private final DayQueryPlanNode dayQueryPlanNode;
    private final FoodRecommendNode foodRecommendNode;
    private final DayDataFetchNode dayDataFetchNode;
    private final DayDataRankNode dayDataRankNode;
    private final DayPlanGenerateNode dayPlanGenerateNode;
    private final TripTimelineAssembler tripTimelineAssembler;
    private final DayPlanValidateNode dayPlanValidateNode;

    private CompiledGraph graph;

    @PostConstruct
    public void init() {
        this.graph = compile();
    }

    public GenerateWorkflowContext execute(GenerateWorkflowContext context) {
        return graph.invoke(toState(context)).map(TripDayGenerateWorkflow::readContext).orElse(context);
    }

    private CompiledGraph compile() {
        try {
            StateGraph stateGraph =
                    new StateGraph(
                            "trip-day-generate-workflow",
                            new KeyStrategyFactoryBuilder()
                                    .addStrategy(CONTEXT, new ReplaceStrategy())
                                    .addStrategy(DAY_CONTEXTS, new ReplaceStrategy())
                                    .addStrategy(DAY_QUERY_PLANS, new ReplaceStrategy())
                                    .addStrategy(LOCKED_DAILY_PLANS, new ReplaceStrategy())
                                    .build());

            stateGraph.addNode("day-context-build", node("day-context-build", dayContextBuildNode::execute));
            stateGraph.addNode("day-context-filter", node("day-context-filter", this::filterTargetDay));
            stateGraph.addNode("day-query-plan", node("day-query-plan", dayQueryPlanNode::execute));
            stateGraph.addNode("food-recommend", node("food-recommend", foodRecommendNode::execute));
            stateGraph.addNode("day-data-fetch", node("day-data-fetch", dayDataFetchNode::execute));
            stateGraph.addNode("day-data-rank", node("day-data-rank", dayDataRankNode::execute));
            stateGraph.addNode("previous-days-snapshot", node("previous-days-snapshot", this::snapshotPreviousDays));
            stateGraph.addNode("day-plan-generate", node("day-plan-generate", dayPlanGenerateNode::execute));
            stateGraph.addNode("trip-timeline-assemble", node("trip-timeline-assemble", this::assembleTimeline));
            stateGraph.addNode("day-plan-validate", node("day-plan-validate", dayPlanValidateNode::execute));

            stateGraph.addEdge(StateGraph.START, "day-context-build");
            stateGraph.addEdge("day-context-build", "day-context-filter");
            stateGraph.addEdge("day-context-filter", "day-query-plan");
            stateGraph.addEdge("day-query-plan", "food-recommend");
            stateGraph.addEdge("food-recommend", "day-data-fetch");
            stateGraph.addEdge("day-data-fetch", "day-data-rank");
            stateGraph.addEdge("day-data-rank", "previous-days-snapshot");
            stateGraph.addEdge("previous-days-snapshot", "day-plan-generate");
            stateGraph.addEdge("day-plan-generate", "trip-timeline-assemble");
            stateGraph.addEdge("trip-timeline-assemble", "day-plan-validate");
            stateGraph.addEdge("day-plan-validate", StateGraph.END);
            return stateGraph.compile();
        } catch (GraphStateException exception) {
            throw new IllegalStateException("Failed to compile trip day generate graph", exception);
        }
    }

    private AsyncNodeAction node(String nodeName, NodeExecutor executor) {
        return AsyncNodeAction.node_async(
                state -> {
                    GenerateWorkflowContext context = readContext(state);
                    long start = WorkflowTiming.start();
                    try {
                        executor.execute(context);
                    } finally {
                        org.slf4j.LoggerFactory.getLogger(WorkflowTiming.class)
                                .info(
                                        "行程生成耗时 workflow=trip-day-generate-workflow node={} elapsedMs={}",
                                        nodeName,
                                        WorkflowTiming.elapsedMs(start));
                    }
                    return toState(context);
                });
    }

    private void filterTargetDay(GenerateWorkflowContext context) {
        Integer dayNo = context.getTargetDayNo();
        context.setDayContexts(
                context.getDayContexts().stream()
                        .filter(dayContext -> dayContext.getDay().equals(dayNo))
                        .toList());
        if (context.getDayContexts().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "行程天数不存在：" + dayNo);
        }
    }

    private void snapshotPreviousDays(GenerateWorkflowContext context) {
        context.setPreviousDailyPlans(
                context.getLockedDailyPlans() == null ? List.of() : context.getLockedDailyPlans());
    }

    private void assembleTimeline(GenerateWorkflowContext context) {
        List<TripPlanDTO.DailyPlan> previousDays =
                context.getPreviousDailyPlans() == null ? List.of() : context.getPreviousDailyPlans();
        tripTimelineAssembler.assemble(previousDays, context.getLockedDailyPlans(), context);
    }

    private static GenerateWorkflowContext readContext(OverAllState state) {
        return (GenerateWorkflowContext)
                state.value(CONTEXT)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Spring AI Alibaba Graph state is missing " + CONTEXT));
    }

    private static Map<String, Object> toState(GenerateWorkflowContext context) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put(CONTEXT, context);
        state.put(DAY_CONTEXTS, context.getDayContexts());
        state.put(DAY_QUERY_PLANS, context.getDayQueryPlans());
        state.put(LOCKED_DAILY_PLANS, context.getLockedDailyPlans());
        return state;
    }

    @FunctionalInterface
    private interface NodeExecutor {
        void execute(GenerateWorkflowContext context) throws Exception;
    }
}
