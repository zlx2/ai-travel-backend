package com.sora.aitravel.workflow.rentalpay;

import com.sora.aitravel.service.RentalOrderService;
import com.sora.aitravel.workflow.WorkflowNode;
import org.springframework.stereotype.Component;

@Component
public class RentalPayProcessNode implements WorkflowNode<RentalPayWorkflowContext> {
    private final RentalOrderService rentalOrderService;

    public RentalPayProcessNode(RentalOrderService rentalOrderService) {
        this.rentalOrderService = rentalOrderService;
    }

    @Override
    public void execute(RentalPayWorkflowContext context) {
        rentalOrderService.pay(context.getUserId(), context.getOrderId(), context.getRequest());
    }
}
