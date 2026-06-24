package com.sora.aitravel.workflow.rentalquote;

import org.springframework.stereotype.Component;

@Component
public class RentalQuotePreviewWorkflow {
    private final RentalQuoteRequirementValidateNode validateNode;
    private final RentalQuoteCalculateNode calculateNode;

    public RentalQuotePreviewWorkflow(
            RentalQuoteRequirementValidateNode validateNode,
            RentalQuoteCalculateNode calculateNode) {
        this.validateNode = validateNode;
        this.calculateNode = calculateNode;
    }

    public RentalQuotePreviewWorkflowContext execute(RentalQuotePreviewWorkflowContext context) {
        validateNode.execute(context);
        calculateNode.execute(context);
        return context;
    }
}
