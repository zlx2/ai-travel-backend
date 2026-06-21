package com.sora.aitravel.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求 DTO。
 *
 * @param account  登录账号（用户名或邮箱）
 * @param password 登录密码
 */
public record LoginRequest(@NotBlank String account, @NotBlank String password) {}
