package com.sora.aitravel.dto.request;

import jakarta.validation.constraints.*;
import java.util.List;

/**
 * 创建游记请求 DTO。
 *
 * @param title       游记标题（必填）
 * @param coverUrl    封面图片 URL
 * @param destination 关联目的地
 * @param summary     游记摘要
 * @param content     游记正文内容（必填）
 * @param tagIds      关联标签 ID 列表
 * @param status      发布状态（0-草稿，1-已发布）
 */
public record CreateNoteRequest(
        @NotBlank String title,
        String coverUrl,
        String destination,
        String summary,
        @NotBlank String content,
        List<Long> tagIds,
        @NotNull @Min(0) @Max(1) Integer status) {}
