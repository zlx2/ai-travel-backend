package com.sora.aitravel.service;

import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.request.TripGenerateRequest;

public interface TravelRequirementReadyService {

    void validateForGenerate(TripGenerateRequest request);

    void resolveRouteScopeIfMissing(TravelRequirementDTO requirement);
}
