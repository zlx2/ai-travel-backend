package com.sora.aitravel.workflow.rentalpay;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.sora.aitravel.workflow.AlibabaGraphWorkflow;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RentalPayWorkflow {

    private final CompiledGraph graph;

    public RentalPayWorkflow(
            RentalPayRequestValidateNode validateNode, RentalPayProcessNode processNode) {
        this.graph =
                AlibabaGraphWorkflow.<RentalPayWorkflowContext>compile(
                        "rental-pay-workflow",
                        List.of(
                                AlibabaGraphWorkflow.step(
                                        "request-validate", validateNode::execute),
                                AlibabaGraphWorkflow.step("pay-process", processNode::execute)));
    }

    public RentalPayWorkflowContext execute(RentalPayWorkflowContext context) {
        return AlibabaGraphWorkflow.invoke(graph, context);
    }
}
