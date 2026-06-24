package com.sora.aitravel.service;

import com.sora.aitravel.dto.model.RentalQuoteOptionDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.response.RentalQuotePreviewResponse;

public interface RentalQuoteService {
    RentalQuotePreviewResponse preview(TravelRequirementDTO requirement);

    RentalQuoteOptionDTO recalculate(
            TravelRequirementDTO requirement, RentalQuoteOptionDTO selectedQuote);
}
