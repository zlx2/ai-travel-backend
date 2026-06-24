package com.sora.aitravel.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新用户个人资料请求 DTO。
 *
 * @param nickname 用户昵称（必填，最长 50 字符）
 * @param avatarUrl 头像图片 URL（可选，最长 500 字符）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserProfileRequest {

    @NotBlank @Size(max = 50) private String nickname;

    @Size(max = 500) private String avatarUrl;
}
