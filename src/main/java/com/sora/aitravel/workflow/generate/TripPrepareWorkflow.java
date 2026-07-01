package com.sora.aitravel.workflow.generate;

import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.CANDIDATE_POOL;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.CITY_PROFILE;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.DAY_SKELETONS;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.HOTEL_SEARCH_RESULT;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.RENTAL_TRIP_CONTEXT;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.REQUIREMENT;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.SELECTED_QUOTE;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.WEATHER_FORECAST;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.workflow.generate.TripPrepareInput;
import com.sora.aitravel.dto.workflow.generate.TripPrepareResult;
import com.sora.aitravel.model.trip.generate.CandidatePool;
import com.sora.aitravel.model.trip.generate.CityProfile;
import com.sora.aitravel.model.trip.generate.DaySkeleton;
import com.sora.aitravel.workflow.generate.node.prepare.AiMacroRoutePlanNode;
import com.sora.aitravel.workflow.generate.node.prepare.AiRouteCriticNode;
import com.sora.aitravel.workflow.generate.node.prepare.AmapMacroRouteFactNode;
import com.sora.aitravel.workflow.generate.node.prepare.DayStateInitNode;
import com.sora.aitravel.workflow.generate.node.prepare.DestinationPrepareNode;
import com.sora.aitravel.workflow.generate.node.prepare.ExternalContextPrepareNode;
import com.sora.aitravel.workflow.generate.node.prepare.MacroRouteContractValidateNode;
import com.sora.aitravel.workflow.generate.state.TripGraphNodeActions;
import com.sora.aitravel.workflow.generate.state.TripGraphStateCodec;
import com.sora.aitravel.workflow.generate.state.TripGraphStateStrategies;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 基于 Spring AI Alibaba Graph 的行程生成准备阶段工作流。 */
@Component
@RequiredArgsConstructor
public class TripPrepareWorkflow {
    private static final String WORKFLOW_NAME = "trip-prepare-workflow";

    private final DestinationPrepareNode destinationPrepareNode;
    private final AiMacroRoutePlanNode aiMacroRoutePlanNode;
    private final AmapMacroRouteFactNode amapMacroRouteFactNode;
    private final AiRouteCriticNode aiRouteCriticNode;
    private final MacroRouteContractValidateNode macroRouteContractValidateNode;
    private final ExternalContextPrepareNode externalContextPrepareNode;
    private final DayStateInitNode dayStateInitNode;

    private CompiledGraph graph;

    @PostConstruct
    public void init() {
        this.graph = compile();
    }

    public TripPrepareResult execute(TripPrepareInput input) {
        Map<String, Object> initialState =
                TripGraphStateCodec.patch(
                        REQUIREMENT, input.getRequirement(),
                        SELECTED_QUOTE, input.getSelectedQuote(),
                        RENTAL_TRIP_CONTEXT, input.getRentalTripContext());
        OverAllState finalState =
                graph.invoke(initialState)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.AI_GENERATE_ERROR, "行程准备工作流执行失败"));
        return new TripPrepareResult(
                TripGraphStateCodec.required(finalState, REQUIREMENT, TravelRequirementDTO.class),
                TripGraphStateCodec.optionalList(finalState, DAY_SKELETONS, DaySkeleton.class),
                TripGraphStateCodec.optional(finalState, CITY_PROFILE, CityProfile.class)
                        .orElse(null),
                TripGraphStateCodec.optional(finalState, WEATHER_FORECAST, String.class)
                        .orElse(null),
                TripGraphStateCodec.optional(finalState, HOTEL_SEARCH_RESULT, String.class)
                        .orElse(null));
    }

    private CompiledGraph compile() {
        try {
            StateGraph stateGraph = new StateGraph(WORKFLOW_NAME, TripGraphStateStrategies.build());

            stateGraph.addNode(
                    "destination-prepare",
                    stateNode("destination-prepare", destinationPrepareNode::execute));
            stateGraph.addNode(
                    "ai-macro-route-plan",
                    stateNode("ai-macro-route-plan", aiMacroRoutePlanNode::execute));
            stateGraph.addNode(
                    "amap-macro-route-fact",
                    stateNode("amap-macro-route-fact", amapMacroRouteFactNode::execute));
            stateGraph.addNode(
                    "ai-route-critic", stateNode("ai-route-critic", aiRouteCriticNode::execute));
            stateGraph.addNode(
                    "macro-route-contract-validate",
                    stateNode(
                            "macro-route-contract-validate",
                            macroRouteContractValidateNode::execute));
            stateGraph.addNode(
                    "prepared-context-validate",
                    stateNode("prepared-context-validate", this::validatePreparedState));
            stateGraph.addNode(
                    "external-context-prepare",
                    stateNode("external-context-prepare", externalContextPrepareNode::execute));
            stateGraph.addNode(
                    "day-state-init", stateNode("day-state-init", dayStateInitNode::execute));

            stateGraph.addEdge(StateGraph.START, "destination-prepare");
            stateGraph.addEdge("destination-prepare", "ai-macro-route-plan");
            stateGraph.addEdge("ai-macro-route-plan", "amap-macro-route-fact");
            stateGraph.addEdge("amap-macro-route-fact", "ai-route-critic");
            stateGraph.addEdge("ai-route-critic", "macro-route-contract-validate");
            stateGraph.addEdge("macro-route-contract-validate", "prepared-context-validate");
            stateGraph.addEdge("prepared-context-validate", "external-context-prepare");
            stateGraph.addEdge("external-context-prepare", "day-state-init");
            stateGraph.addEdge("day-state-init", StateGraph.END);
            return stateGraph.compile();
        } catch (GraphStateException exception) {
            throw new IllegalStateException("Failed to compile trip prepare graph", exception);
        }
    }

    private AsyncNodeAction stateNode(
            String nodeName, TripGraphNodeActions.StateNodeExecutor executor) {
        return TripGraphNodeActions.stateNode(WORKFLOW_NAME, nodeName, executor::execute);
    }

    private Map<String, Object> validatePreparedState(OverAllState state) {
        TravelRequirementDTO requirement =
                TripGraphStateCodec.required(state, REQUIREMENT, TravelRequirementDTO.class);
        List<DaySkeleton> daySkeletons =
                TripGraphStateCodec.optionalList(state, DAY_SKELETONS, DaySkeleton.class);
        CandidatePool candidatePool =
                TripGraphStateCodec.required(state, CANDIDATE_POOL, CandidatePool.class);
        int days = requirement.getDays();
        if (daySkeletons == null || daySkeletons.size() != days) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "行程骨架数量与天数不一致");
        }
        if (candidatePool.getScenicCandidates() == null
                || candidatePool.getScenicCandidates().isEmpty()) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "目的地景点候选为空");
        }
        return Map.of();
    }
}
