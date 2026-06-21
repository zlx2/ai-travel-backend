package com.sora.aitravel.dto.request;

import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.model.TripPlanDTO;
import jakarta.validation.constraints.*;
import java.util.List;

public record SaveTripRequest(
        String conversationId,
        String title,
        @NotBlank String departure,
        @NotBlank String destination,
        @NotNull @Min(1) @Max(7) Integer days,
        Integer budget,
        List<String> preferences,
        TravelRequirementDTO requirementJson,
        TripPlanDTO tripPlanJson,
        String summary,
        String coverUrl) {}
