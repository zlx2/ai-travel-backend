package com.sora.aitravel.workflow.rentalpay;

import com.sora.aitravel.service.RentalOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RentalPayProcessNode {
    private final RentalOrderService rentalOrderService;

    public void execute(RentalPayWorkflowContext context) {
        rentalOrderService.pay(context.getUserId(), context.getOrderId(), context.getRequest());
    }
}
