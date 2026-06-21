package com.sora.aitravel.service;

import com.sora.aitravel.common.result.PageResult;
import com.sora.aitravel.dto.request.CreateCommentRequest;
import com.sora.aitravel.dto.response.CommentResponse;

public interface CommentService {
    PageResult<CommentResponse> list(Long noteId, Integer pageNum, Integer pageSize);

    CommentResponse create(Long noteId, CreateCommentRequest request);

    void delete(Long id);
}
