package com.sora.aitravel.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建评论请求 DTO。
 *
 * @param content 评论内容（必填，最多 500 字）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateCommentRequest {

    @NotBlank @Size(max = 500) private String content;
}
