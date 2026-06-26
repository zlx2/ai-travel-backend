package com.sora.aitravel.dto.response;

/** AI 行程单日生成响应。 */
public record TripGenerateDayResponse(
        String sessionId,
        Integer dayNo,
        Integer generationVersion,
        Integer isCurrent,
        String status,
        String resultJson,
        String errorMessage) {}
