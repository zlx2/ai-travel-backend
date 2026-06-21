package com.sora.aitravel.dto.request;

import jakarta.validation.constraints.*;

/**
 * 注册请求 DTO。
 *
 * @param username  用户名（必填，最长 50 字符）
 * @param password  密码（必填，6-32 字符）
 * @param email     邮箱地址（必填，最长 100 字符）
 * @param emailCode 邮箱验证码（必填，6 位数字）
 */
public record RegisterRequest(
        @NotBlank @Size(max = 50) String username,
        @NotBlank @Size(min = 6, max = 32) String password,
        @NotBlank @Email @Size(max = 100) String email,
        @NotBlank @Size(min = 6, max = 6) String emailCode) {}
