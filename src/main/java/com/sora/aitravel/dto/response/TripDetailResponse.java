package com.sora.aitravel.dto.response;

import java.util.List;

public record TripDetailResponse(
        Long id,
        String conversationId,
        String title,
        String departure,
        String destination,
        Integer days,
        Integer budget,
        List<String> preferences,
        Object requirementJson,
        Object tripPlanJson,
        String summary,
        String coverUrl,
        Integer source,
        Integer status,
        String createTime,
        String updateTime) {}
