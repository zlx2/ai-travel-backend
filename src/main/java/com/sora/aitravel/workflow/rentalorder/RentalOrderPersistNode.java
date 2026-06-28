package com.sora.aitravel.workflow.rentalorder;

import com.sora.aitravel.dto.request.RentalOrderCreateRequest;
import com.sora.aitravel.service.RentalOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RentalOrderPersistNode {
    private final RentalOrderService rentalOrderService;

    public void execute(RentalOrderCreateWorkflowContext context) {
        RentalOrderCreateRequest request = context.getRequest();
        if (context.getRecalculatedQuote() != null) {
            request =
                    new RentalOrderCreateRequest(
                            request.getConversationId(),
                            request.getRequirement(),
                            request.getTripPlan(),
                            context.getRecalculatedQuote(),
                            request.getProtectionPackageCode(),
                            request.getProtectionPackageName(),
                            request.getProtectionFeeCent(),
                            request.getContactName(),
                            request.getContactPhone(),
                            request.getRemark());
        }
        context.setOrderId(rentalOrderService.create(context.getUserId(), request));
    }
}
