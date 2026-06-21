package com.sora.aitravel.dto.request;

import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotBlank @Size(max = 50) String username,
        @NotBlank @Size(min = 6, max = 32) String password,
        @NotBlank @Email @Size(max = 100) String email,
        @NotBlank @Size(min = 6, max = 6) String emailCode) {}
