package com.sora.aitravel.service;

import com.sora.aitravel.entity.AiTripGenerationSession;
import com.sora.aitravel.dto.request.TripGenerateRequest;

/** AI 行程生成编排服务。 */
public interface AiTripGenerationOrchestrator {

    AiTripGenerationSession prepareSession(Long userId, TripGenerateRequest request);
}
