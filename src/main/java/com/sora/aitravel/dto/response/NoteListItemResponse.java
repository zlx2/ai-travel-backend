package com.sora.aitravel.dto.response;

public record NoteListItemResponse(
        Long id,
        String title,
        String coverUrl,
        String destination,
        String summary,
        Long authorId,
        String authorNickname,
        String authorAvatarUrl,
        Integer likeCount,
        Integer favoriteCount,
        Integer commentCount,
        String createTime) {}
