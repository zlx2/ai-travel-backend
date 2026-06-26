package com.sora.aitravel.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 修改邮箱请求 DTO。
 *
 * @param newEmail 新邮箱地址（必填，最长 100 字符）
 * @param emailCode 邮箱验证码（必填，6 位）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserEmailRequest {

    @NotBlank @Email @Size(max = 100) private String newEmail;

    @NotBlank @Size(min = 6, max = 6) private String emailCode;
}
