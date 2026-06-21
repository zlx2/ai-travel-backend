package com.sora.aitravel.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * AI 聊天请求 DTO。
 *
 * <p>一期 mode 只能为 TRIP，且 tripId 必须属于当前登录用户。</p>
 *
 * @param mode    对话模式（一期仅支持 "TRIP"）
 * @param tripId  关联的旅行计划 ID
 * @param message 用户发送的消息内容
 */
public record AiChatRequest(
        @NotBlank String mode, @NotNull Long tripId, @NotBlank String message) {}
