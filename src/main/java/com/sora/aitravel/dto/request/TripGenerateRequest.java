package com.sora.aitravel.dto.request;

import com.sora.aitravel.dto.model.ConflictDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * AI 旅行计划生成请求 DTO。
 *
 * <p>confirmedConflict=true 时，生成结果的 tips 必须包含对应风险提示。</p>
 *
 * @param conversationId     AI 对话 ID（用于关联多轮对话上下文）
 * @param confirmedConflict  用户是否已确认接受冲突风险
 * @param requirement        结构化旅行需求（必填）
 * @param conflicts          检测到的冲突列表
 */
public record TripGenerateRequest(
        String conversationId,
        Boolean confirmedConflict,
        @NotNull @Valid TravelRequirementDTO requirement,
        List<ConflictDTO> conflicts) {}
