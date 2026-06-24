package com.sora.aitravel.dto.request;

import com.sora.aitravel.dto.model.TravelRequirementDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record RentalQuotePreviewRequest(@NotNull @Valid TravelRequirementDTO requirement) {}
