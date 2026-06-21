package com.sora.aitravel.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建评论请求 DTO。
 *
 * @param content 评论内容（必填，最多 500 字）
 */
public record CreateCommentRequest(@NotBlank @Size(max = 500) String content) {}
