package com.sora.aitravel.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 邮箱验证码请求 DTO。
 *
 * @param email 目标邮箱地址（必填，最长 100 字符）
 * @param scene 验证码场景（如 register-注册、login-登录、reset_pwd-重置密码等）
 */
public record EmailCodeRequest(
        @NotBlank @Email @Size(max = 100) String email, @NotBlank String scene) {}
