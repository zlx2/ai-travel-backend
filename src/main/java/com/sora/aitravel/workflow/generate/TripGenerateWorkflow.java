package com.sora.aitravel.workflow.generate;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.sora.aitravel.workflow.AlibabaGraphWorkflow;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class TripGenerateWorkflow {

    private final CompiledGraph graph;
    private final GenerateResponseNormalizer responseNormalizer;

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
            DayDataFetchNode dayDataFetchNode,
            DayDataRankNode dayDataRankNode,
            DayPlanGenerateNode dayPlanGenerateNode,
            DayPlanValidateNode dayPlanValidateNode,
            TripSummaryNode tripSummaryNode,
            GenerateResultMergeNode generateResultMergeNode,
            GenerateResponseNormalizer responseNormalizer) {
        this.responseNormalizer = responseNormalizer;
        this.graph =
                AlibabaGraphWorkflow.compile(
                        "trip-generate-workflow",
                        List.of(
                                AlibabaGraphWorkflow.step(
                                        "requirement-validate", requirementValidateNode::execute),
                                AlibabaGraphWorkflow.step(
                                        "requirement-load", requirementLoadNode::execute),
                                AlibabaGraphWorkflow.step(
                                        "trip-skeleton", tripSkeletonNode::execute),
                                AlibabaGraphWorkflow.step(
                                        "city-data-profile", cityDataProfileNode::execute),
                                AlibabaGraphWorkflow.step(
                                        "weather-fetch", weatherFetchNode::execute),
                                AlibabaGraphWorkflow.step(
                                        "hotel-fetch", hotelFetchNode::execute),
                                AlibabaGraphWorkflow.step(
                                        "day-state-init", dayStateInitNode::execute),
                                AlibabaGraphWorkflow.step(
                                        "day-context-build", dayContextBuildNode::execute),
                                AlibabaGraphWorkflow.step(
                                        "day-query-plan", dayQueryPlanNode::execute),
                                AlibabaGraphWorkflow.step(
                                        "day-data-fetch", dayDataFetchNode::execute),
                                AlibabaGraphWorkflow.step(
                                        "day-data-rank", dayDataRankNode::execute),
                                AlibabaGraphWorkflow.step(
                                        "day-plan-generate", dayPlanGenerateNode::execute),
                                AlibabaGraphWorkflow.step(
                                        "day-plan-validate", dayPlanValidateNode::execute),
                                AlibabaGraphWorkflow.step("trip-summary", tripSummaryNode::execute),
                                AlibabaGraphWorkflow.step(
                                        "result-merge", generateResultMergeNode::execute)));
    }

    public GenerateWorkflowContext execute(GenerateWorkflowContext context) {
        GenerateWorkflowContext result = AlibabaGraphWorkflow.invoke(graph, context);
        responseNormalizer.normalize(result);
        return result;
    }
}
