package com.sora.aitravel.workflow.rentalquote;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RentalQuotePreviewWorkflow {

    private final RentalQuoteRequirementValidateNode validateNode;
    private final RentalQuoteCalculateNode calculateNode;

    public RentalQuotePreviewWorkflowContext execute(RentalQuotePreviewWorkflowContext context) {
        validateNode.execute(context);
        calculateNode.execute(context);
        return context;
    }
}
