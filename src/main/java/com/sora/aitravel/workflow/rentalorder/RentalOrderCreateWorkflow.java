package com.sora.aitravel.workflow.rentalorder;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RentalOrderCreateWorkflow {

    private final RentalOrderRequestValidateNode validateNode;
    private final RentalOrderQuoteRecalculateNode quoteRecalculateNode;
    private final RentalOrderPersistNode persistNode;

    public RentalOrderCreateWorkflowContext execute(RentalOrderCreateWorkflowContext context) {
        validateNode.execute(context);
        quoteRecalculateNode.execute(context);
        persistNode.execute(context);
        return context;
    }
}
