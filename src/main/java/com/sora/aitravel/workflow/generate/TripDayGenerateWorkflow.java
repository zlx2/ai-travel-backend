package com.sora.aitravel.workflow.generate;

import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.CITY_PROFILE;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.DAY_CONTEXTS;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.DAY_SKELETONS;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.DAY_VALIDATION_REPORTS;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.HOTEL_SEARCH_RESULT;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.LOCKED_DAILY_PLANS;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.PREVIOUS_DAILY_PLANS;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.RENTAL_TRIP_CONTEXT;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.REQUIREMENT;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.REVISION_TEXT;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.SELECTED_QUOTE;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.TARGET_DAY_NO;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.WEATHER_FORECAST;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.dto.model.TripPlanDTO;
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

    public DayGenerateResult execute(DayGenerateInput input) {
        Map<String, Object> initialState = TripGraphStateCodec.patch(
                REQUIREMENT, input.getRequirement(),
                DAY_SKELETONS, input.getDaySkeletons(),
                SELECTED_QUOTE, input.getSelectedQuote(),
                RENTAL_TRIP_CONTEXT, input.getRentalTripContext(),
                CITY_PROFILE, input.getCityProfile(),
                WEATHER_FORECAST, input.getWeatherForecast(),
                HOTEL_SEARCH_RESULT, input.getHotelSearchResult(),
                PREVIOUS_DAILY_PLANS, input.getPreviousDailyPlans(),
                TARGET_DAY_NO, input.getTargetDayNo(),
                REVISION_TEXT, input.getRevisionText());
        OverAllState finalState = graph.invoke(initialState)
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_GENERATE_ERROR, "单日行程生成失败"));
        List<TripPlanDTO.DailyPlan> lockedPlans =
                TripGraphStateCodec.optionalList(finalState, LOCKED_DAILY_PLANS, TripPlanDTO.DailyPlan.class);
        TripPlanDTO.DailyPlan dailyPlan = lockedPlans.stream()
                .filter(item -> input.getTargetDayNo().equals(item.getDay()))
                .findFirst()
                .orElseGet(() -> lockedPlans.isEmpty() ? null : lockedPlans.get(0));
        if (dailyPlan == null) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "单日行程生成结果为空，day=" + input.getTargetDayNo());
        }
        return new DayGenerateResult(
                dailyPlan,
                lockedPlans,
                TripGraphStateCodec.optionalList(finalState, DAY_VALIDATION_REPORTS, DayPlanValidationReport.class));
    }

    private CompiledGraph compile() {
        try {
            StateGraph stateGraph =
                    new StateGraph(
                            WORKFLOW_NAME,
                            TripGraphStateStrategies.build());

            stateGraph.addNode("day-context-build", stateNode("day-context-build", dayContextBuildNode::execute));
            stateGraph.addNode("day-context-filter", stateNode("day-context-filter", this::filterTargetDay));
            stateGraph.addNode("day-query-plan", stateNode("day-query-plan", dayQueryPlanNode::execute));
            stateGraph.addNode("food-recommend", stateNode("food-recommend", foodRecommendNode::execute));
            stateGraph.addNode("day-data-fetch", stateNode("day-data-fetch", dayDataFetchNode::execute));
            stateGraph.addNode("day-data-rank", stateNode("day-data-rank", dayDataRankNode::execute));
            stateGraph.addNode("previous-days-snapshot", stateNode("previous-days-snapshot", this::snapshotPreviousDays));
            stateGraph.addNode("day-plan-generate", stateNode("day-plan-generate", dayPlanGenerateNode::execute));
            stateGraph.addNode("trip-timeline-assemble", stateNode("trip-timeline-assemble", tripTimelineAssembler::execute));
            stateGraph.addNode("day-plan-validate", stateNode("day-plan-validate", dayPlanValidateNode::execute));

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

    private AsyncNodeAction stateNode(String nodeName, TripGraphNodeActions.StateNodeExecutor executor) {
        return TripGraphNodeActions.stateNode(WORKFLOW_NAME, nodeName, executor::execute);
    }

    private Map<String, Object> filterTargetDay(OverAllState state) {
        Integer dayNo = TripGraphStateCodec.required(state, TARGET_DAY_NO, Integer.class);
        List<DayContext> filtered =
                TripGraphStateCodec.optionalList(state, DAY_CONTEXTS, DayContext.class).stream()
                        .filter(dayContext -> dayContext.getDay().equals(dayNo))
                        .toList();
        if (filtered.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "行程天数不存在：" + dayNo);
        }
        return TripGraphStateCodec.patch(DAY_CONTEXTS, filtered);
    }

    private Map<String, Object> snapshotPreviousDays(OverAllState state) {
        List<TripPlanDTO.DailyPlan> lockedDailyPlans =
                TripGraphStateCodec.optionalList(state, LOCKED_DAILY_PLANS, TripPlanDTO.DailyPlan.class);
        return TripGraphStateCodec.patch(PREVIOUS_DAILY_PLANS, lockedDailyPlans);
    }

}
