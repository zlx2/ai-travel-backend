package com.sora.aitravel.dto.response;

public record TripListItemResponse(
        Long id,
        String title,
        String departure,
        String destination,
        Integer days,
        Integer budget,
        String summary,
        String coverUrl,
        String createTime) {}
