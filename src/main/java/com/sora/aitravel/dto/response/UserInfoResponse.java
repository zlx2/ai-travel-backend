package com.sora.aitravel.dto.response;

public record UserInfoResponse(
        Long id,
        String username,
        String nickname,
        String email,
        String avatarUrl,
        Integer role,
        Integer status,
        String createTime) {}
