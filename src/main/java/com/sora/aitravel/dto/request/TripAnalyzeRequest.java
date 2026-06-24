package com.sora.aitravel.dto.request;

import com.sora.aitravel.dto.model.TravelRequirementDTO;
import java.util.List;

/** AI 旅行需求分析请求。 */
public record TripAnalyzeRequest(
        String conversationId,
        String userInput,
        TravelRequirementDTO formInput,
        List<String> extraAnswers,
        TravelRequirementDTO requirement,
        String selectedDestination) {}
