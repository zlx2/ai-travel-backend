package com.sora.aitravel.workflow.generate;

import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.CITY_PROFILE;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.DAY_SKELETONS;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.HOTEL_SEARCH_RESULT;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.RENTAL_TRIP_CONTEXT;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.REQUIREMENT;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.SELECTED_QUOTE;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.WEATHER_FORECAST;

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
import com.sora.aitravel.model.CityProfile;
import com.sora.aitravel.model.DaySkeleton;
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
    private final MacroRoutePrepareNode macroRoutePrepareNode;
    private final ExternalContextPrepareNode externalContextPrepareNode;
    private final PrepareFinalizeNode prepareFinalizeNode;

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
                    "macro-route-prepare",
                    stateNode("macro-route-prepare", macroRoutePrepareNode::execute));
            stateGraph.addNode(
                    "external-context-prepare",
                    stateNode("external-context-prepare", externalContextPrepareNode::execute));
            stateGraph.addNode(
                    "prepare-finalize",
                    stateNode("prepare-finalize", prepareFinalizeNode::execute));

            stateGraph.addEdge(StateGraph.START, "destination-prepare");
            stateGraph.addEdge("destination-prepare", "macro-route-prepare");
            stateGraph.addEdge("macro-route-prepare", "external-context-prepare");
            stateGraph.addEdge("external-context-prepare", "prepare-finalize");
            stateGraph.addEdge("prepare-finalize", StateGraph.END);
            return stateGraph.compile();
        } catch (GraphStateException exception) {
            throw new IllegalStateException("Failed to compile trip prepare graph", exception);
        }
    }

    private AsyncNodeAction stateNode(
            String nodeName, TripGraphNodeActions.StateNodeExecutor executor) {
        return TripGraphNodeActions.stateNode(WORKFLOW_NAME, nodeName, executor::execute);
    }
}
