package com.sora.aitravel.dto.response;

/** AI 行程生成会话响应。 */
public record TripGenerateSessionResponse(
        String sessionId, String conversationId, String status, String errorMessage) {}
