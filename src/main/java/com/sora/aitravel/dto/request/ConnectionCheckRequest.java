package com.sora.aitravel.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 前后端联调检查请求 DTO。
 * <p>前端发起联调检查时携带的最小请求。</p>
 *
 * @param action 联调动作标识（由前端自定义，后端原样回显）
 */
public record ConnectionCheckRequest(@NotBlank(message = "联调动作不能为空") String action) {}
