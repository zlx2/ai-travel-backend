package com.sora.aitravel.dto.request;

import jakarta.validation.constraints.NotBlank;

/** 前端发起联调检查时携带的最小请求。 */
public record ConnectionCheckRequest(@NotBlank(message = "联调动作不能为空") String action) {}
