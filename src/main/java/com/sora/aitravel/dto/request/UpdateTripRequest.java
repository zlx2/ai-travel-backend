package com.sora.aitravel.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新旅行计划请求 DTO。
 *
 * @param title 行程标题（必填）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTripRequest {

    @NotBlank private String title;
}
