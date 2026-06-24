package com.sora.aitravel.dto.request;

import com.sora.aitravel.dto.model.RentalQuoteOptionDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.model.TripPlanDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record RentalOrderCreateRequest(
        String conversationId,
        @NotNull @Valid TravelRequirementDTO requirement,
        @NotNull @Valid TripPlanDTO tripPlan,
        @NotNull RentalQuoteOptionDTO selectedQuote,
        String contactName,
        String contactPhone,
        String remark) {}
