package com.sora.aitravel.workflow.rental;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.sora.aitravel.workflow.AlibabaGraphWorkflow;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RentalWorkflow {

    private final CompiledGraph graph;

    public RentalWorkflow(RentalStoreResolveNode rentalStoreResolveNode) {
        this.graph =
                AlibabaGraphWorkflow.compile(
                        "rental-workflow",
                        List.of(
                                AlibabaGraphWorkflow.step(
                                        "rental-store-resolve", rentalStoreResolveNode::execute)));
    }

    public RentalWorkflowContext execute(RentalWorkflowContext context) {
        return AlibabaGraphWorkflow.invoke(graph, context);
    }
}
