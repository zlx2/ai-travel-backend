package com.sora.aitravel.dto.request;

import jakarta.validation.constraints.*;

/**
 * 标签操作请求 DTO（管理后台创建/更新标签用）。
 *
 * @param name   标签名称（必填）
 * @param type   标签类型（1-目的地标签，2-游记标签，3-偏好标签）
 * @param status 状态（0-禁用，1-启用）
 */
public record TagRequest(
        @NotBlank String name,
        @NotNull @Min(1) @Max(3) Integer type,
        @NotNull @Min(0) @Max(1) Integer status) {}
