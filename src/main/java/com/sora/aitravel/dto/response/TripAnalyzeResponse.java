package com.sora.aitravel.dto.response;

import com.sora.aitravel.dto.model.*;
import java.util.List;

public record TripAnalyzeResponse(
        String conversationId,
        String status,
        TravelRequirementDTO requirement,
        List<QuestionDTO> questions,
        List<DestinationSuggestionDTO> destinationSuggestions,
        List<ConflictDTO> conflicts,
        Integer askRound) {}
