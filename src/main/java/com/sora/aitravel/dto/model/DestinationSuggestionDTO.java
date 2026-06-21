package com.sora.aitravel.dto.model;

import java.util.List;

public record DestinationSuggestionDTO(
        String name, String reason, List<String> tags, Integer recommendedDays) {}
