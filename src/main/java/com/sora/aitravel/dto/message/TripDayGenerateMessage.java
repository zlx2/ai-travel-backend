package com.sora.aitravel.dto.message;

/** 单日行程生成 MQ 消息。 */
public record TripDayGenerateMessage(
        String sessionId,
        Long userId,
        Integer dayNo,
        String requestMode,
        Boolean forceRegenerate,
        String requestId) {}
