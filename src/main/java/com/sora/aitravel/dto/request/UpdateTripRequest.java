package com.sora.aitravel.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 更新旅行计划请求 DTO。
 *
 * @param title 行程标题（必填）
 */
public record UpdateTripRequest(@NotBlank String title) {}
