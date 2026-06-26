package com.sora.aitravel.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 标签响应 DTO。
 *
 * @param id 标签 ID
 * @param name 标签名称
 * @param type 标签类型（1-目的地标签，2-游记标签，3-偏好标签）
 * @param status 状态（0-禁用，1-启用）
 * @param createTime 创建时间
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagResponse {

    private Long id;
    private String name;
    private Integer type;
    private Integer status;
    private String createTime;
}
