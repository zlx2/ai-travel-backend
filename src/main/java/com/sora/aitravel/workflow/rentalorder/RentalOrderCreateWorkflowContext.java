package com.sora.aitravel.workflow.rentalorder;

import com.sora.aitravel.dto.request.RentalOrderCreateRequest;
import com.sora.aitravel.dto.model.RentalQuoteOptionDTO;
import lombok.Data;

@Data
public class RentalOrderCreateWorkflowContext {
    private Long userId;
    private RentalOrderCreateRequest request;
    private RentalQuoteOptionDTO recalculatedQuote;
    private Long orderId;
}
