package com.sora.aitravel.dto.workflow.generate;

import com.sora.aitravel.dto.model.RentalQuoteOptionDTO;
import com.sora.aitravel.dto.model.RentalTripContextDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TripPrepareInput {
    private Long userId;
    private TravelRequirementDTO requirement;
    private RentalQuoteOptionDTO selectedQuote;
    private RentalTripContextDTO rentalTripContext;
}
