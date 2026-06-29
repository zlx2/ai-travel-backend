package com.sora.aitravel.workflow.rentalcontext;

import com.sora.aitravel.dto.model.RentalArrivalPointDTO;
import com.sora.aitravel.dto.model.RentalQuoteOptionDTO;
import com.sora.aitravel.dto.model.RentalStoreDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.request.RentalContextPreviewRequest;
import com.sora.aitravel.dto.response.RentalContextPreviewResponse;
import java.util.List;
import lombok.Data;

@Data
public class RentalContextPreviewWorkflowContext {

    private Long userId;
    private RentalContextPreviewRequest request;
    private TravelRequirementDTO requirement;
    private Boolean rentalRecommended;
    private String recommendReason;
    private RentalArrivalPointDTO arrivalPoint;
    private RentalStoreDTO matchedStore;
    private List<RentalQuoteOptionDTO> quoteOptions;
    private RentalContextPreviewResponse result;
}
