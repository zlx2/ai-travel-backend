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
import jakarta.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 基于 Spring AI Alibaba Graph 的行程生成准备阶段工作流。 */
@Component
@RequiredArgsConstructor
public class TripPrepareWorkflow {

    private static final String CONTEXT = "context";
    private static final String REQUIREMENT = "requirement";
    private static final String CITY_PROFILE = "cityProfile";
    private static final String CANDIDATE_POOL = "candidatePool";
    private static final String DAY_SKELETONS = "daySkeletons";

    private final RequirementValidateNode requirementValidateNode;
    private final RequirementLoadNode requirementLoadNode;
    private final RouteScopeResolveNode routeScopeResolveNode;
    private final CityDataProfileNode cityDataProfileNode;
    private final CandidatePoolBuildNode candidatePoolBuildNode;
    private final AiMacroRoutePlanNode aiMacroRoutePlanNode;
    private final AmapMacroRouteFactNode amapMacroRouteFactNode;
    private final AiRouteCriticNode aiRouteCriticNode;
    private final MacroRouteContractValidateNode macroRouteContractValidateNode;
    private final WeatherFetchNode weatherFetchNode;
    private final HotelFetchNode hotelFetchNode;
    private final DayStateInitNode dayStateInitNode;

    private CompiledGraph graph;

    @PostConstruct
    public void init() {
        this.graph = compile();
    }

    public GenerateWorkflowContext execute(GenerateWorkflowContext context) {
        return graph.invoke(toState(context)).map(TripPrepareWorkflow::readContext).orElse(context);
    }

    private CompiledGraph compile() {
        try {
            StateGraph stateGraph =
                    new StateGraph(
                            "trip-prepare-workflow",
                            new KeyStrategyFactoryBuilder()
                                    .addStrategy(CONTEXT, new ReplaceStrategy())
                                    .addStrategy(REQUIREMENT, new ReplaceStrategy())
                                    .addStrategy(CITY_PROFILE, new ReplaceStrategy())
                                    .addStrategy(CANDIDATE_POOL, new ReplaceStrategy())
                                    .addStrategy(DAY_SKELETONS, new ReplaceStrategy())
                                    .build());

            stateGraph.addNode("requirement-validate", node("requirement-validate", requirementValidateNode::execute));
            stateGraph.addNode("requirement-load", node("requirement-load", requirementLoadNode::execute));
            stateGraph.addNode("route-scope-resolve", node("route-scope-resolve", routeScopeResolveNode::execute));
            stateGraph.addNode("city-data-profile", node("city-data-profile", cityDataProfileNode::execute));
            stateGraph.addNode("candidate-pool-build", node("candidate-pool-build", candidatePoolBuildNode::execute));
            stateGraph.addNode("ai-macro-route-plan", node("ai-macro-route-plan", aiMacroRoutePlanNode::execute));
            stateGraph.addNode("amap-macro-route-fact", node("amap-macro-route-fact", amapMacroRouteFactNode::execute));
            stateGraph.addNode("ai-route-critic", node("ai-route-critic", aiRouteCriticNode::execute));
            stateGraph.addNode(
                    "macro-route-contract-validate",
                    node("macro-route-contract-validate", macroRouteContractValidateNode::execute));
            stateGraph.addNode("prepared-context-validate", node("prepared-context-validate", this::validatePreparedContext));
            stateGraph.addNode("weather-fetch", node("weather-fetch", weatherFetchNode::execute));
            stateGraph.addNode("hotel-fetch", node("hotel-fetch", hotelFetchNode::execute));
            stateGraph.addNode("day-state-init", node("day-state-init", dayStateInitNode::execute));

            stateGraph.addEdge(StateGraph.START, "requirement-validate");
            stateGraph.addEdge("requirement-validate", "requirement-load");
            stateGraph.addEdge("requirement-load", "route-scope-resolve");
            stateGraph.addEdge("route-scope-resolve", "city-data-profile");
            stateGraph.addEdge("city-data-profile", "candidate-pool-build");
            stateGraph.addEdge("candidate-pool-build", "ai-macro-route-plan");
            stateGraph.addEdge("ai-macro-route-plan", "amap-macro-route-fact");
            stateGraph.addEdge("amap-macro-route-fact", "ai-route-critic");
            stateGraph.addEdge("ai-route-critic", "macro-route-contract-validate");
            stateGraph.addEdge("macro-route-contract-validate", "prepared-context-validate");
            stateGraph.addEdge("prepared-context-validate", "weather-fetch");
            stateGraph.addEdge("weather-fetch", "hotel-fetch");
            stateGraph.addEdge("hotel-fetch", "day-state-init");
            stateGraph.addEdge("day-state-init", StateGraph.END);
            return stateGraph.compile();
        } catch (GraphStateException exception) {
            throw new IllegalStateException("Failed to compile trip prepare graph", exception);
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
                                        "行程生成耗时 workflow=trip-prepare-workflow node={} elapsedMs={}",
                                        nodeName,
                                        WorkflowTiming.elapsedMs(start));
                    }
                    return toState(context);
                });
    }

    private void validatePreparedContext(GenerateWorkflowContext context) {
        int days = context.getRequirement().getDays();
        if (context.getDaySkeletons() == null || context.getDaySkeletons().size() != days) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "行程骨架数量与天数不一致");
        }
        if (!context.hasScenicCandidates()) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "目的地景点候选为空");
        }
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
        state.put(REQUIREMENT, context.getRequirement());
        state.put(CITY_PROFILE, context.getCityProfile());
        state.put(CANDIDATE_POOL, context.getCandidatePool());
        state.put(DAY_SKELETONS, context.getDaySkeletons());
        return state;
    }

    @FunctionalInterface
    private interface NodeExecutor {
        void execute(GenerateWorkflowContext context) throws Exception;
    }
}
