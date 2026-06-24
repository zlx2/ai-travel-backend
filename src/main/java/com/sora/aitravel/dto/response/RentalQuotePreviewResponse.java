package com.sora.aitravel.dto.response;

import com.sora.aitravel.dto.model.RentalQuoteOptionDTO;
import java.util.List;

public record RentalQuotePreviewResponse(
        String routeMode,
        String rentalCity,
        String citycode,
        List<RentalQuoteOptionDTO> quoteOptions) {}
