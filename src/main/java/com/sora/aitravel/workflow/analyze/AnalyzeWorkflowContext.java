package com.sora.aitravel.workflow.analyze;

import com.sora.aitravel.dto.request.TripAnalyzeRequest;
import com.sora.aitravel.dto.response.TripAnalyzeResponse;
import lombok.Data;

@Data
public class AnalyzeWorkflowContext {
    private Long userId;
    private TripAnalyzeRequest request;
    private String rawModelResponse;
    private TripAnalyzeResponse result;
    private boolean repairAttempted;
}
