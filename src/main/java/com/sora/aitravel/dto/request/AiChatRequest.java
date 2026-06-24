package com.sora.aitravel.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 聊天请求 DTO。
 *
 * <p>一期 mode 只能为 TRIP，且 tripId 必须属于当前登录用户。
 *
 * @param mode 对话模式（一期仅支持 "TRIP"）
 * @param tripId 关联的旅行计划 ID
 * @param message 用户发送的消息内容
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiChatRequest {

    @NotBlank private String mode;
    @NotNull private Long tripId;
    @NotBlank private String message;
}
