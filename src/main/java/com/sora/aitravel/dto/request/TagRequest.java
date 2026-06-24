package com.sora.aitravel.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 标签操作请求 DTO（管理后台创建/更新标签用）。
 *
 * @param name 标签名称（必填）
 * @param type 标签类型（1-目的地标签，2-游记标签，3-偏好标签）
 * @param status 状态（0-禁用，1-启用）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TagRequest {

    @NotBlank private String name;

    @NotNull @Min(1) @Max(3) private Integer type;

    @NotNull @Min(0) @Max(1) private Integer status;
}
