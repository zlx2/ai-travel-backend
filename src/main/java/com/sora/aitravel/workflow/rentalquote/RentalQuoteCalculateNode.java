package com.sora.aitravel.workflow.rentalquote;

import com.sora.aitravel.service.RentalQuoteService;
import org.springframework.stereotype.Component;

@Component
public class RentalQuoteCalculateNode {
    private final RentalQuoteService rentalQuoteService;

    public RentalQuoteCalculateNode(RentalQuoteService rentalQuoteService) {
        this.rentalQuoteService = rentalQuoteService;
    }

    public void execute(RentalQuotePreviewWorkflowContext context) {
        context.setResult(rentalQuoteService.preview(context.getRequirement()));
    }
}
