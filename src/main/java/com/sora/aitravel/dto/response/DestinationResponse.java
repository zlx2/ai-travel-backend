package com.sora.aitravel.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record DestinationResponse(
        Long id,
        String name,
        String province,
        String city,
        BigDecimal longitude,
        BigDecimal latitude,
        String coverUrl,
        String description,
        List<String> tags,
        Integer heat,
        Integer status,
        String createTime) {}
