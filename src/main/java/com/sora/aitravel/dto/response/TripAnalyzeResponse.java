package com.sora.aitravel.dto.response;

import com.sora.aitravel.dto.model.*;
import java.util.List;

/**
 * AI 旅行需求分析响应 DTO。
 *
 * @param conversationId        AI 对话 ID
 * @param status                分析状态（如 analyzing-分析中, completed-完成, need_more_info-需要更多信息）
 * @param requirement           结构化提取的旅行需求
 * @param questions             需要追问用户的问题列表
 * @param destinationSuggestions AI 推荐的目的地列表
 * @param conflicts             检测到的需求冲突列表
 * @param askRound              当前追问轮次
 */
public record TripAnalyzeResponse(
        String conversationId,
        String status,
        TravelRequirementDTO requirement,
        List<QuestionDTO> questions,
        List<DestinationSuggestionDTO> destinationSuggestions,
        List<ConflictDTO> conflicts,
        Integer askRound) {}
