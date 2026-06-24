package com.sora.aitravel.workflow.rentalorder;

import org.springframework.stereotype.Component;

@Component
public class RentalOrderCreateWorkflow {
    private final RentalOrderRequestValidateNode validateNode;
    private final RentalOrderQuoteRecalculateNode quoteRecalculateNode;
    private final RentalOrderPersistNode persistNode;

    public RentalOrderCreateWorkflow(
            RentalOrderRequestValidateNode validateNode,
            RentalOrderQuoteRecalculateNode quoteRecalculateNode,
            RentalOrderPersistNode persistNode) {
        this.validateNode = validateNode;
        this.quoteRecalculateNode = quoteRecalculateNode;
        this.persistNode = persistNode;
    }

    public RentalOrderCreateWorkflowContext execute(RentalOrderCreateWorkflowContext context) {
        validateNode.execute(context);
        quoteRecalculateNode.execute(context);
        persistNode.execute(context);
        return context;
    }
}
