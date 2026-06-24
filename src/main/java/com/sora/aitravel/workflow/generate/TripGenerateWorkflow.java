package com.sora.aitravel.workflow.generate;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.sora.aitravel.workflow.AlibabaGraphWorkflow;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TripGenerateWorkflow {

    private final CompiledGraph graph;

    public TripGenerateWorkflow(
            RequirementValidateNode requirementValidateNode,
            TravelModeDecisionNode travelModeDecisionNode,
            MockScenicSpotRecommendNode mockScenicSpotRecommendNode,
            MockFoodRecommendNode mockFoodRecommendNode,
            MockHotelAreaRecommendNode mockHotelAreaRecommendNode,
            TransportRecommendNode transportRecommendNode,
            RecommendationPromptBuildNode recommendationPromptBuildNode,
            TripPlanGenerateNode tripPlanGenerateNode,
            GenerateJsonValidateNode generateJsonValidateNode,
            GenerateJsonRepairNode generateJsonRepairNode,
            GenerateResultMergeNode generateResultMergeNode) {
        this.graph =
                AlibabaGraphWorkflow.<GenerateWorkflowContext>compile(
                        "trip-generate-workflow",
                        List.of(
                                AlibabaGraphWorkflow.step(
                                        "requirement-validate", requirementValidateNode::execute),
                                AlibabaGraphWorkflow.step(
                                        "travel-mode-decision", travelModeDecisionNode::execute),
                                AlibabaGraphWorkflow.step(
                                        "scenic-spot-recommend",
                                        mockScenicSpotRecommendNode::execute),
                                AlibabaGraphWorkflow.step(
                                        "food-recommend", mockFoodRecommendNode::execute),
                                AlibabaGraphWorkflow.step(
                                        "hotel-area-recommend",
                                        mockHotelAreaRecommendNode::execute),
                                AlibabaGraphWorkflow.step(
                                        "transport-recommend", transportRecommendNode::execute),
                                AlibabaGraphWorkflow.step(
                                        "recommendation-prompt-build",
                                        recommendationPromptBuildNode::execute),
                                AlibabaGraphWorkflow.step(
                                        "trip-plan-generate", tripPlanGenerateNode::execute),
                                AlibabaGraphWorkflow.step(
                                        "json-validate", generateJsonValidateNode::execute),
                                AlibabaGraphWorkflow.step(
                                        "json-repair", generateJsonRepairNode::execute),
                                AlibabaGraphWorkflow.step(
                                        "result-merge", generateResultMergeNode::execute)));
    }

    public GenerateWorkflowContext execute(GenerateWorkflowContext context) {
        return AlibabaGraphWorkflow.invoke(graph, context);
    }
}
