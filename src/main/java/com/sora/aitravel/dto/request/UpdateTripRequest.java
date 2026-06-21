package com.sora.aitravel.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateTripRequest(@NotBlank String title) {}
