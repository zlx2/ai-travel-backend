package com.sora.aitravel.workflow.rentalpay;

import com.sora.aitravel.dto.request.RentalOrderPayRequest;
import lombok.Data;

@Data
public class RentalPayWorkflowContext {
    private Long userId;
    private Long orderId;
    private RentalOrderPayRequest request;
}
