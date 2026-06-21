package com.sora.aitravel.dto.response;

/**
 * 游记列表项响应 DTO（列表展示用，不含正文内容）。
 *
 * @param id              游记 ID
 * @param title           游记标题
 * @param coverUrl        封面图片 URL
 * @param destination     关联目的地
 * @param summary         游记摘要
 * @param authorId        作者用户 ID
 * @param authorNickname  作者昵称
 * @param authorAvatarUrl 作者头像 URL
 * @param likeCount       点赞数
 * @param favoriteCount   收藏数
 * @param commentCount    评论数
 * @param createTime      创建时间
 */
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
