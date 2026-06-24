package com.sora.aitravel.workflow.rentalorder;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.sora.aitravel.workflow.AlibabaGraphWorkflow;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RentalOrderCreateWorkflow {

    private final CompiledGraph graph;

    public RentalOrderCreateWorkflow(
            RentalOrderRequestValidateNode validateNode,
            RentalOrderQuoteRecalculateNode quoteRecalculateNode,
            RentalOrderPersistNode persistNode) {
        this.graph =
                AlibabaGraphWorkflow.compile(
                        "rental-order-create-workflow",
                        List.of(
                                AlibabaGraphWorkflow.step(
                                        "request-validate", validateNode::execute),
                                AlibabaGraphWorkflow.step(
                                        "quote-recalculate", quoteRecalculateNode::execute),
                                AlibabaGraphWorkflow.step("persist", persistNode::execute)));
    }

    public RentalOrderCreateWorkflowContext execute(RentalOrderCreateWorkflowContext context) {
        return AlibabaGraphWorkflow.invoke(graph, context);
    }
}
