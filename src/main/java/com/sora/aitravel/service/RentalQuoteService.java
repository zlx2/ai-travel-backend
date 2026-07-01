package com.sora.aitravel.service;

import com.sora.aitravel.dto.model.RentalQuoteOptionDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.request.RentalContextPreviewRequest;
import com.sora.aitravel.dto.response.RentalContextPreviewResponse;
import com.sora.aitravel.dto.response.RentalQuotePreviewResponse;
import java.util.List;

public interface RentalQuoteService {
    RentalContextPreviewResponse previewContext(RentalContextPreviewRequest request);

    RentalQuotePreviewResponse preview(TravelRequirementDTO requirement);

    RentalQuoteOptionDTO recalculate(
            TravelRequirementDTO requirement, RentalQuoteOptionDTO selectedQuote);

    List<RentalQuoteOptionDTO> latestOrderedOptions(int limit);
}
