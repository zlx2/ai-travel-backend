package com.sora.aitravel.dto.request;

import java.util.List;

/** userInput 与 extraAnswers 不能同时为空；selectedDestination 用于确认推荐结果。 */
public record TripAnalyzeRequest(
        String conversationId,
        String userInput,
        List<String> extraAnswers,
        String selectedDestination) {}
