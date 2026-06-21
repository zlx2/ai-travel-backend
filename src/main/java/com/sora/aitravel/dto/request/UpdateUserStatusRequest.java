package com.sora.aitravel.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 更新用户状态请求 DTO（管理后台用）。
 *
 * @param status 目标状态（0-禁用，1-启用）
 */
public record UpdateUserStatusRequest(@NotNull @Min(0) @Max(1) Integer status) {}
