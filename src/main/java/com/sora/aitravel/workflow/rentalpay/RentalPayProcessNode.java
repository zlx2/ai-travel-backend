package com.sora.aitravel.workflow.rentalpay;

import com.sora.aitravel.service.RentalOrderService;
import org.springframework.stereotype.Component;

@Component
public class RentalPayProcessNode {
    private final RentalOrderService rentalOrderService;

    public RentalPayProcessNode(RentalOrderService rentalOrderService) {
        this.rentalOrderService = rentalOrderService;
    }

    public void execute(RentalPayWorkflowContext context) {
        rentalOrderService.pay(context.getUserId(), context.getOrderId(), context.getRequest());
    }
}
