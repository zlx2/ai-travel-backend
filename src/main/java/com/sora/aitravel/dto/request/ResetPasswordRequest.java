package com.sora.aitravel.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 重置密码请求 DTO。
 *
 * @param email 邮箱地址
 * @param emailCode 邮箱验证码
 * @param newPassword 新密码
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordRequest {

    @NotBlank @Email @Size(max = 100) private String email;

    @NotBlank @Size(min = 6, max = 6) private String emailCode;

    @NotBlank @Size(min = 6, max = 50) private String newPassword;
}
