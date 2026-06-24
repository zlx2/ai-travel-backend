package com.sora.aitravel.workflow.rentalquote;

import com.sora.aitravel.service.RentalQuoteService;
import com.sora.aitravel.workflow.WorkflowNode;
import org.springframework.stereotype.Component;

@Component
public class RentalQuoteCalculateNode implements WorkflowNode<RentalQuotePreviewWorkflowContext> {
    private final RentalQuoteService rentalQuoteService;

    public RentalQuoteCalculateNode(RentalQuoteService rentalQuoteService) {
        this.rentalQuoteService = rentalQuoteService;
    }

    @Override
    public void execute(RentalQuotePreviewWorkflowContext context) {
        context.setResult(rentalQuoteService.preview(context.getRequirement()));
    }
}
