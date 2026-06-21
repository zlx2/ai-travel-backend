package com.sora.aitravel.dto.response;

import java.util.List;

public record NoteDetailResponse(
        Long id,
        String title,
        String coverUrl,
        String destination,
        String summary,
        String content,
        Long authorId,
        String authorNickname,
        String authorAvatarUrl,
        List<TagResponse> tags,
        Integer viewCount,
        Integer likeCount,
        Integer favoriteCount,
        Integer commentCount,
        Boolean liked,
        Boolean favorited,
        Integer status,
        String createTime,
        String updateTime) {}
