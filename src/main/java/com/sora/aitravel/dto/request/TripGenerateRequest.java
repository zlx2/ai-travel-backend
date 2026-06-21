package com.sora.aitravel.dto.request;

import com.sora.aitravel.dto.model.ConflictDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** confirmedConflict=true 时，生成结果的 tips 必须包含对应风险提示。 */
public record TripGenerateRequest(
        String conversationId,
        Boolean confirmedConflict,
        @NotNull @Valid TravelRequirementDTO requirement,
        List<ConflictDTO> conflicts) {}
