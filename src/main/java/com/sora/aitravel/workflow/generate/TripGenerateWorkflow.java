package com.sora.aitravel.workflow.generate;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.sora.aitravel.workflow.AlibabaGraphWorkflow;
import java.util.List;
import java.util.function.BiConsumer;
import org.springframework.stereotype.Component;

@Component
public class TripGenerateWorkflow {

    private final CompiledGraph graph;
    private final GenerateResponseNormalizer responseNormalizer;
    private final List<AlibabaGraphWorkflow.Step<GenerateWorkflowContext>> steps;

    public TripGenerateWorkflow(
            RequirementValidateNode requirementValidateNode,
            RequirementLoadNode requirementLoadNode,
            TripSkeletonNode tripSkeletonNode,
            CityDataProfileNode cityDataProfileNode,
            WeatherFetchNode weatherFetchNode,
            HotelFetchNode hotelFetchNode,
            DayStateInitNode dayStateInitNode,
            DayContextBuildNode dayContextBuildNode,
            DayQueryPlanNode dayQueryPlanNode,
            FoodRecommendNode foodRecommendNode,
            DayDataFetchNode dayDataFetchNode,
            DayDataRankNode dayDataRankNode,
            DayPlanGenerateNode dayPlanGenerateNode,
            TripTimelineAssembler tripTimelineAssembler,
            DayPlanValidateNode dayPlanValidateNode,
            TripSummaryNode tripSummaryNode,
            GenerateResultMergeNode generateResultMergeNode,
            GenerateResponseNormalizer responseNormalizer) {
        this.responseNormalizer = responseNormalizer;
        this.steps =
                List.of(
                        AlibabaGraphWorkflow.step(
                                "requirement-validate", requirementValidateNode::execute),
                        AlibabaGraphWorkflow.step("requirement-load", requirementLoadNode::execute),
                        AlibabaGraphWorkflow.step("trip-skeleton", tripSkeletonNode::execute),
                        AlibabaGraphWorkflow.step(
                                "city-data-profile", cityDataProfileNode::execute),
                        AlibabaGraphWorkflow.step("weather-fetch", weatherFetchNode::execute),
                        AlibabaGraphWorkflow.step("hotel-fetch", hotelFetchNode::execute),
                        AlibabaGraphWorkflow.step("day-state-init", dayStateInitNode::execute),
                        AlibabaGraphWorkflow.step(
                                "day-context-build", dayContextBuildNode::execute),
                        AlibabaGraphWorkflow.step("day-query-plan", dayQueryPlanNode::execute),
                        AlibabaGraphWorkflow.step("food-recommend", foodRecommendNode::execute),
                        AlibabaGraphWorkflow.step("day-data-fetch", dayDataFetchNode::execute),
                        AlibabaGraphWorkflow.step("day-data-rank", dayDataRankNode::execute),
                        AlibabaGraphWorkflow.step(
                                "day-plan-generate", dayPlanGenerateNode::execute),
                        AlibabaGraphWorkflow.step(
                                "trip-timeline-assemble", tripTimelineAssembler::execute),
                        AlibabaGraphWorkflow.step(
                                "day-plan-validate", dayPlanValidateNode::execute),
                        AlibabaGraphWorkflow.step("trip-summary", tripSummaryNode::execute),
                        AlibabaGraphWorkflow.step(
                                "result-merge", generateResultMergeNode::execute));
        this.graph = AlibabaGraphWorkflow.compile("trip-generate-workflow", steps);
    }

    public GenerateWorkflowContext execute(GenerateWorkflowContext context) {
        GenerateWorkflowContext result = AlibabaGraphWorkflow.invoke(graph, context);
        responseNormalizer.normalize(result);
        return result;
    }

    public GenerateWorkflowContext executeWithProgress(
            GenerateWorkflowContext context, BiConsumer<String, Integer> progress) {
        int total = steps.size();
        for (int index = 0; index < total; index++) {
            AlibabaGraphWorkflow.Step<GenerateWorkflowContext> step = steps.get(index);
            progress.accept(step.getName(), Math.max(1, (index * 100) / total));
            try {
                step.getAction().execute(context);
            } catch (Exception ex) {
                throw new IllegalStateException(
                        "Trip generate workflow node failed: " + step.getName(), ex);
            }
            progress.accept(step.getName(), Math.min(99, ((index + 1) * 100) / total));
        }
        responseNormalizer.normalize(context);
        return context;
    }
}
