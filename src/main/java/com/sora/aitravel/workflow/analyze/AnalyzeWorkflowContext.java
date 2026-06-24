package com.sora.aitravel.workflow.analyze;

import com.sora.aitravel.dto.model.ConflictDTO;
import com.sora.aitravel.dto.model.DestinationSuggestionDTO;
import com.sora.aitravel.dto.model.QuestionDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.request.TripAnalyzeRequest;
import com.sora.aitravel.dto.response.TripAnalyzeResponse;
import java.util.List;
import lombok.Data;

/** Analyze 工作流上下文。 */
@Data
public class AnalyzeWorkflowContext {

    private Long userId;
    private TripAnalyzeRequest request;

    private String cleanInput;
    private TravelRequirementDTO extractedRequirement;
    private List<QuestionDTO> questions;
    private List<DestinationSuggestionDTO> destinationSuggestions;
    private List<ConflictDTO> conflicts;

    private String status;
    private TripAnalyzeResponse result;
}
