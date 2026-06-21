package com.sora.aitravel.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.sora.aitravel.common.result.*;
import com.sora.aitravel.dto.request.CreateCommentRequest;
import com.sora.aitravel.dto.response.CommentResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class CommentController {
    @GetMapping("/notes/{id}/comments")
    public R<PageResult<CommentResponse>> list(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return ScaffoldResponses.notImplemented();
    }

    @SaCheckLogin
    @PostMapping("/notes/{id}/comments")
    public R<CommentResponse> create(
            @PathVariable Long id, @Valid @RequestBody CreateCommentRequest request) {
        return ScaffoldResponses.notImplemented();
    }

    @SaCheckLogin
    @DeleteMapping("/comments/{id}")
    public R<Void> delete(@PathVariable Long id) {
        return ScaffoldResponses.notImplemented();
    }
}
