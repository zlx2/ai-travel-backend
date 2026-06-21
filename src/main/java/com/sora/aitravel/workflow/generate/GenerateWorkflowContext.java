package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.dto.request.TripGenerateRequest;
import com.sora.aitravel.dto.response.TripGenerateResponse;
import lombok.Data;

@Data
public class GenerateWorkflowContext {
    private Long userId;
    private TripGenerateRequest request;
    private String rawModelResponse;
    private TripGenerateResponse result;
    private boolean repairAttempted;
}
