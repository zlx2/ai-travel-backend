package com.sora.aitravel.dto.response;

/**
 * 评论响应 DTO。
 *
 * @param id         评论 ID
 * @param noteId     所属游记 ID
 * @param userId     评论作者的用户 ID
 * @param nickname   评论作者昵称
 * @param avatarUrl  评论作者头像 URL
 * @param content    评论内容
 * @param createTime 评论创建时间
 */
public record CommentResponse(
        Long id,
        Long noteId,
        Long userId,
        String nickname,
        String avatarUrl,
        String content,
        String createTime) {}
