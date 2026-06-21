package com.sora.aitravel.dto.request;

import jakarta.validation.constraints.*;

public record TagRequest(
        @NotBlank String name,
        @NotNull @Min(1) @Max(3) Integer type,
        @NotNull @Min(0) @Max(1) Integer status) {}
