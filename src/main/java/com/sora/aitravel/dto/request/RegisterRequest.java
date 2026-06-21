package com.sora.aitravel.dto.request;

import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotBlank String username,
        @NotBlank @Size(min = 6, max = 32) String password,
        @NotBlank @Email String email,
        @NotBlank String emailCode) {}
