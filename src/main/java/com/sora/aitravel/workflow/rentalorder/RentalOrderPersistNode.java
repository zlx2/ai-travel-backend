package com.sora.aitravel.workflow.rentalorder;

import com.sora.aitravel.dto.request.RentalOrderCreateRequest;
import com.sora.aitravel.service.RentalOrderService;
import org.springframework.stereotype.Component;

@Component
public class RentalOrderPersistNode {
    private final RentalOrderService rentalOrderService;

    public RentalOrderPersistNode(RentalOrderService rentalOrderService) {
        this.rentalOrderService = rentalOrderService;
    }

    public void execute(RentalOrderCreateWorkflowContext context) {
        RentalOrderCreateRequest request = context.getRequest();
        if (context.getRecalculatedQuote() != null) {
            request =
                    new RentalOrderCreateRequest(
                            request.conversationId(),
                            request.requirement(),
                            request.tripPlan(),
                            context.getRecalculatedQuote(),
                            request.contactName(),
                            request.contactPhone(),
                            request.remark());
        }
        context.setOrderId(rentalOrderService.create(context.getUserId(), request));
    }
}
