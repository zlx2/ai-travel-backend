package com.sora.aitravel.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发送修改邮箱验证码请求 DTO。
 *
 * @param newEmail 新邮箱地址（必填，最长 100 字符）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendChangeEmailCodeRequest {

    @NotBlank @Email @Size(max = 100) private String newEmail;
}
