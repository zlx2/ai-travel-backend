package com.sora.aitravel.dto.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;

public record TravelRequirementDTO(
        String departure,
        String destination,
        @Min(1) @Max(7) Integer days,
        Integer budget,
        String budgetType,
        Integer peopleCount,
        List<String> preferences,
        String pace,
        List<String> avoidances,
        String travelDate) {}
