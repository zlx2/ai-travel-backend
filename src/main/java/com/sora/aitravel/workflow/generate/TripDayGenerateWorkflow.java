package com.sora.aitravel.workflow.generate;

import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.CITY_PROFILE;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.DAY_SKELETONS;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.DAY_VALIDATION_REPORTS;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.HOTEL_SEARCH_RESULT;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.LOCKED_DAILY_PLANS;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.PREVIOUS_DAILY_PLANS;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.RENTAL_TRIP_CONTEXT;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.REQUIREMENT;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.REVISION_TEXT;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.SELECTED_QUOTE;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.SINGLE_DAY_GENERATION;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.TARGET_DAY_NO;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.WEATHER_FORECAST;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.dto.model.TripPlanDTO;
import com.sora.aitravel.dto.workflow.generate.DayGenerateInput;
import com.sora.aitravel.dto.workflow.generate.DayGenerateResult;
import com.sora.aitravel.model.trip.generate.DayPlanValidationReport;
import com.sora.aitravel.workflow.generate.node.day.DayCandidatePrepareNode;
import com.sora.aitravel.workflow.generate.node.day.DayInputPrepareNode;
import com.sora.aitravel.workflow.generate.node.day.DayPlanFinalizeNode;
import com.sora.aitravel.workflow.generate.node.day.DayPlanGenerateNode;
import com.sora.aitravel.workflow.generate.state.TripGraphNodeActions;
import com.sora.aitravel.workflow.generate.state.TripGraphStateCodec;
import com.sora.aitravel.workflow.generate.state.TripGraphStateStrategies;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 基于 Spring AI Alibaba Graph 的单日行程生成工作流。 */
@Component
@RequiredArgsConstructor
public class TripDayGenerateWorkflow {
    private static final String WORKFLOW_NAME = "trip-day-generate-workflow";

    private final DayInputPrepareNode dayInputPrepareNode;
    private final DayCandidatePrepareNode dayCandidatePrepareNode;
    private final DayPlanGenerateNode dayPlanGenerateNode;
    private final DayPlanFinalizeNode dayPlanFinalizeNode;

    private CompiledGraph graph;

    @PostConstruct
    public void init() {
        this.graph = compile();
    }

    public DayGenerateResult execute(DayGenerateInput input) {
        Map<String, Object> initialState =
                TripGraphStateCodec.patch(
                        REQUIREMENT, input.getRequirement(),
                        DAY_SKELETONS, input.getDaySkeletons(),
                        SELECTED_QUOTE, input.getSelectedQuote(),
                        RENTAL_TRIP_CONTEXT, input.getRentalTripContext(),
                        CITY_PROFILE, input.getCityProfile(),
                        WEATHER_FORECAST, input.getWeatherForecast(),
                        HOTEL_SEARCH_RESULT, input.getHotelSearchResult(),
                        LOCKED_DAILY_PLANS, input.getPreviousDailyPlans(),
                        PREVIOUS_DAILY_PLANS, input.getPreviousDailyPlans(),
                        TARGET_DAY_NO, input.getTargetDayNo(),
                        REVISION_TEXT, input.getRevisionText(),
                        SINGLE_DAY_GENERATION, true);
        OverAllState finalState =
                graph.invoke(initialState)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.AI_GENERATE_ERROR, "单日行程生成失败"));
        List<TripPlanDTO.DailyPlan> lockedPlans =
                TripGraphStateCodec.optionalList(
                        finalState, LOCKED_DAILY_PLANS, TripPlanDTO.DailyPlan.class);
        TripPlanDTO.DailyPlan dailyPlan =
                lockedPlans.stream()
                        .filter(item -> input.getTargetDayNo().equals(item.getDay()))
                        .findFirst()
                        .orElseGet(() -> lockedPlans.isEmpty() ? null : lockedPlans.get(0));
        if (dailyPlan == null) {
            throw new BusinessException(
                    ErrorCode.AI_GENERATE_ERROR, "单日行程生成结果为空，day=" + input.getTargetDayNo());
        }
        return new DayGenerateResult(
                dailyPlan,
                lockedPlans,
                TripGraphStateCodec.optionalList(
                        finalState, DAY_VALIDATION_REPORTS, DayPlanValidationReport.class));
    }

    private CompiledGraph compile() {
        try {
            StateGraph stateGraph = new StateGraph(WORKFLOW_NAME, TripGraphStateStrategies.build());

            stateGraph.addNode(
                    "day-input-prepare",
                    stateNode("day-input-prepare", dayInputPrepareNode::execute));
            stateGraph.addNode(
                    "day-candidate-prepare",
                    stateNode("day-candidate-prepare", dayCandidatePrepareNode::execute));
            stateGraph.addNode(
                    "day-plan-generate",
                    stateNode("day-plan-generate", dayPlanGenerateNode::execute));
            stateGraph.addNode(
                    "day-plan-finalize",
                    stateNode("day-plan-finalize", dayPlanFinalizeNode::execute));

            stateGraph.addEdge(StateGraph.START, "day-input-prepare");
            stateGraph.addEdge("day-input-prepare", "day-candidate-prepare");
            stateGraph.addEdge("day-candidate-prepare", "day-plan-generate");
            stateGraph.addEdge("day-plan-generate", "day-plan-finalize");
            stateGraph.addEdge("day-plan-finalize", StateGraph.END);
            return stateGraph.compile();
        } catch (GraphStateException exception) {
            throw new IllegalStateException("Failed to compile trip day generate graph", exception);
        }
    }

    private AsyncNodeAction stateNode(
            String nodeName, TripGraphNodeActions.StateNodeExecutor executor) {
        return TripGraphNodeActions.stateNode(WORKFLOW_NAME, nodeName, executor::execute);
    }
}
