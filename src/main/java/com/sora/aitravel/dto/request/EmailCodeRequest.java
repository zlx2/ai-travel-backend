package com.sora.aitravel.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 邮箱验证码请求 DTO。
 *
 * @param email 目标邮箱地址（必填，最长 100 字符）
 * @param scene 验证码场景（如 register-注册、login-登录、reset_pwd-重置密码等）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailCodeRequest {

    @NotBlank @Email @Size(max = 100) private String email;

    @NotBlank private String scene;
}
