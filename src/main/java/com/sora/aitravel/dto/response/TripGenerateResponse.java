package com.sora.aitravel.dto.response;

import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.model.TripPlanDTO;

public record TripGenerateResponse(
        String conversationId, TravelRequirementDTO requirement, TripPlanDTO tripPlan) {}
