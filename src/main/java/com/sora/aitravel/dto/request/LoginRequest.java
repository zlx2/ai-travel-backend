package com.sora.aitravel.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String account, @NotBlank String password) {}
