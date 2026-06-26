package com.sora.aitravel.dto.response;

/** AI 行程按天生成状态。 */
public record TripGenerateDayStatusResponse(
        Integer dayNo,
        Integer generationVersion,
        Integer isCurrent,
        String status,
        String errorMessage) {}
