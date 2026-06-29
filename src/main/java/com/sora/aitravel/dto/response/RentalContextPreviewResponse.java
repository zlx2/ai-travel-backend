package com.sora.aitravel.dto.response;

import com.sora.aitravel.dto.model.RentalArrivalPointDTO;
import com.sora.aitravel.dto.model.RentalPickupPlanDTO;
import com.sora.aitravel.dto.model.RentalQuoteOptionDTO;
import com.sora.aitravel.dto.model.RentalStoreDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentalContextPreviewResponse {

    private Boolean rentalRecommended;
    private String reason;
    private TravelRequirementDTO requirement;
    private RentalArrivalPointDTO arrivalPoint;
    private RentalStoreDTO matchedStore;
    private RentalPickupPlanDTO pickupPlan;
    private List<RentalQuoteOptionDTO> quoteOptions;
}
