package com.sora.aitravel.dto.response;

public record CommentResponse(
        Long id,
        Long noteId,
        Long userId,
        String nickname,
        String avatarUrl,
        String content,
        String createTime) {}
