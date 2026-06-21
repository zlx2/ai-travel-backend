package com.sora.aitravel.dto.request;

import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.model.TripPlanDTO;
import jakarta.validation.constraints.*;
import java.util.List;

/**
 * 保存旅行计划请求 DTO。
 *
 * @param conversationId AI 对话 ID（关联 AI 生成过程的上下文）
 * @param title          行程标题
 * @param departure      出发地（必填）
 * @param destination    目的地（必填）
 * @param days           行程天数（必填，1-7 天）
 * @param budget         预算金额
 * @param preferences    偏好标签列表
 * @param requirementJson 结构化旅行需求（JSON 格式）
 * @param tripPlanJson    完整行程计划（JSON 格式）
 * @param summary        行程摘要
 * @param coverUrl       封面图片 URL
 */
public record SaveTripRequest(
        String conversationId,
        String title,
        @NotBlank String departure,
        @NotBlank String destination,
        @NotNull @Min(1) @Max(7) Integer days,
        Integer budget,
        List<String> preferences,
        TravelRequirementDTO requirementJson,
        TripPlanDTO tripPlanJson,
        String summary,
        String coverUrl) {}
