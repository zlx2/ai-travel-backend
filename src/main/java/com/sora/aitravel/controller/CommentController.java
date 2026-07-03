package com.sora.aitravel.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.sora.aitravel.common.result.*;
import com.sora.aitravel.dto.request.CreateCommentRequest;
import com.sora.aitravel.dto.response.CommentResponse;
import com.sora.aitravel.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 评论控制器。
 *
 * <p>全局 /api 前缀由 server.servlet.context-path 配置。
 *
 * <p>请求方式：RESTful
 *
 * <p>权限要求：评论列表（公开）无需登录；创建和删除评论需登录（@SaCheckLogin）
 */
@RestController
@RequestMapping
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    /**
     * 分页查询指定游记的评论列表（公开接口）。
     *
     * @param id 游记 ID
     * @param pageNum 页码，默认 1
     * @param pageSize 每页条数，默认 10
     * @return 分页的评论列表（CommentResponse）
     */
    @GetMapping("/notes/{id}/comments")
    public R<PageResult<CommentResponse>> list(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.ok(commentService.list(id, pageNum, pageSize));
    }

    /**
     * 创建对指定游记的评论（需登录）。
     *
     * @param id 游记 ID
     * @param request 包含评论内容的请求体
     * @return 创建成功返回新评论的详细信息（CommentResponse）
     */
    @SaCheckLogin
    @PostMapping("/notes/{id}/comments")
    public R<CommentResponse> create(
            @PathVariable Long id, @Valid @RequestBody CreateCommentRequest request) {
        return R.ok(commentService.create(id, request));
    }

    /**
     * 删除指定评论（需登录，仅评论作者可操作）。
     *
     * @param id 评论 ID
     * @return 无返回内容，删除成功即返回成功响应
     */
    @SaCheckLogin
    @DeleteMapping("/comments/{id}")
    public R<Void> delete(@PathVariable Long id) {
        commentService.delete(id);
        return R.ok();
    }
}
