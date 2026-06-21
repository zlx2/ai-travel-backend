package com.sora.aitravel.dto.request;

import java.util.List;

/**
 * AI 旅行需求分析请求 DTO。
 *
 * <p>userInput 与 extraAnswers 不能同时为空；selectedDestination 用于确认推荐结果。</p>
 *
 * @param conversationId      AI 对话 ID（用于关联多轮对话上下文）
 * @param userInput           用户输入的模糊需求文本
 * @param extraAnswers        针对追问问题的扩展答案列表
 * @param selectedDestination 用户确认选择的目的地
 */
public record TripAnalyzeRequest(
        String conversationId,
        String userInput,
        List<String> extraAnswers,
        String selectedDestination) {}
