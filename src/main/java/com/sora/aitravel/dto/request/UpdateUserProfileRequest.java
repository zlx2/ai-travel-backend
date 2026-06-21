package com.sora.aitravel.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 更新用户个人资料请求 DTO。
 *
 * @param nickname  用户昵称（必填，最长 50 字符）
 * @param avatarUrl 头像图片 URL（可选，最长 500 字符）
 */
public record UpdateUserProfileRequest(
        @NotBlank @Size(max = 50) String nickname, @Size(max = 500) String avatarUrl) {}
