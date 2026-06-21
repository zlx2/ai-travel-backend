package com.sora.aitravel.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 一期 mode 只能为 TRIP，且 tripId 必须属于当前登录用户。 */
public record AiChatRequest(
        @NotBlank String mode, @NotNull Long tripId, @NotBlank String message) {}
