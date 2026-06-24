package com.sora.aitravel.workflow.rentalquote;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.sora.aitravel.workflow.AlibabaGraphWorkflow;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RentalQuotePreviewWorkflow {

    private final CompiledGraph graph;

    public RentalQuotePreviewWorkflow(
            RentalQuoteRequirementValidateNode validateNode,
            RentalQuoteCalculateNode calculateNode) {
        this.graph =
                AlibabaGraphWorkflow.compile(
                        "rental-quote-preview-workflow",
                        List.of(
                                AlibabaGraphWorkflow.step(
                                        "requirement-validate", validateNode::execute),
                                AlibabaGraphWorkflow.step(
                                        "quote-calculate", calculateNode::execute)));
    }

    public RentalQuotePreviewWorkflowContext execute(RentalQuotePreviewWorkflowContext context) {
        return AlibabaGraphWorkflow.invoke(graph, context);
    }
}
