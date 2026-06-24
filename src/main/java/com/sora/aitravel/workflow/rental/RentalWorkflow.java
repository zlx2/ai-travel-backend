package com.sora.aitravel.workflow.rental;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RentalWorkflow {

    private final RentalStoreResolveNode rentalStoreResolveNode;

    public RentalWorkflowContext execute(RentalWorkflowContext context) {
        rentalStoreResolveNode.execute(context);
        return context;
    }
}
