package com.sora.aitravel.service;

/**
 * 游记互动服务接口。
 * <p>
 * 提供用户对游记的点赞和收藏等社交互动功能。
 * </p>
 */
public interface NoteInteractionService {
    /**
     * 点赞游记。
     *
     * @param noteId 游记 ID
     */
    void like(Long noteId);

    /**
     * 取消点赞游记。
     *
     * @param noteId 游记 ID
     */
    void unlike(Long noteId);

    /**
     * 收藏游记。
     *
     * @param noteId 游记 ID
     */
    void favorite(Long noteId);

    /**
     * 取消收藏游记。
     *
     * @param noteId 游记 ID
     */
    void unfavorite(Long noteId);
}
