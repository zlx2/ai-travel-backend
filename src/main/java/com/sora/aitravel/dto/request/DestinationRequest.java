package com.sora.aitravel.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;

public record DestinationRequest(
        @NotBlank String name,
        String province,
        String city,
        BigDecimal longitude,
        BigDecimal latitude,
        String coverUrl,
        String description,
        List<String> tags,
        Integer heat,
        Integer status) {}
