package com.sora.aitravel.dto.response;

import java.time.LocalDateTime;

/** 后端收到联调请求后的回显结果。 */
public record ConnectionCheckResponse(
        String message, String receivedAction, LocalDateTime receivedAt) {}
