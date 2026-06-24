package com.sora.aitravel.service;

import com.sora.aitravel.common.result.PageResult;
import com.sora.aitravel.dto.request.CreateCommentRequest;
import com.sora.aitravel.dto.response.CommentResponse;

/**
 * 评论服务接口。
 *
 * <p>提供游记评论的 CRUD 功能，包括评论列表查询、创建评论和删除评论。
 */
public interface CommentService {
    /**
     * 分页查询某篇游记的评论列表。
     *
     * @param noteId 游记 ID
     * @param pageNum 页码（从 1 开始）
     * @param pageSize 每页条数
     * @return 评论分页结果
     */
    PageResult<CommentResponse> list(Long noteId, Integer pageNum, Integer pageSize);

    /**
     * 创建评论。
     *
     * @param noteId 游记 ID
     * @param request 创建评论请求，包含评论内容
     * @return 创建的评论信息
     */
    CommentResponse create(Long noteId, CreateCommentRequest request);

    /**
     * 删除评论。
     *
     * @param id 评论 ID
     */
    void delete(Long id);
}
