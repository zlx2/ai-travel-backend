package com.sora.aitravel.workflow.rentalquote;

import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.response.RentalQuotePreviewResponse;
import lombok.Data;

@Data
public class RentalQuotePreviewWorkflowContext {
    private Long userId;
    private TravelRequirementDTO requirement;
    private RentalQuotePreviewResponse result;
}
