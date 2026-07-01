package com.sora.aitravel.workflow.rentalcontext;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.sora.aitravel.workflow.AlibabaGraphWorkflow;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RentalContextPreviewWorkflow {

    private final CompiledGraph graph;

    public RentalContextPreviewWorkflow(
            RentalContextRequirementNode requirementNode,
            RentalArrivalResolveNode arrivalResolveNode,
            RentalContextStoreResolveNode storeResolveNode,
            RentalContextQuoteRecommendNode quoteRecommendNode,
            RentalContextResultMergeNode resultMergeNode) {
        List<AlibabaGraphWorkflow.Step<RentalContextPreviewWorkflowContext>> steps =
                List.of(
                        AlibabaGraphWorkflow.step(
                                "rental-context-requirement", requirementNode::execute),
                        AlibabaGraphWorkflow.step(
                                "rental-arrival-resolve", arrivalResolveNode::execute),
                        AlibabaGraphWorkflow.step(
                                "rental-store-resolve", storeResolveNode::execute),
                        AlibabaGraphWorkflow.step(
                                "rental-quote-recommend", quoteRecommendNode::execute),
                        AlibabaGraphWorkflow.step(
                                "rental-context-result-merge", resultMergeNode::execute));
        this.graph = AlibabaGraphWorkflow.compile("rental-context-preview-workflow", steps);
    }

    public RentalContextPreviewWorkflowContext execute(
            RentalContextPreviewWorkflowContext context) {
        return AlibabaGraphWorkflow.invoke(graph, context);
    }
}
