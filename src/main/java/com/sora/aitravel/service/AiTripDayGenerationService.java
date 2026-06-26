package com.sora.aitravel.service;

import com.sora.aitravel.entity.AiTripDayGeneration;

/** AI 行程按天生成服务。 */
public interface AiTripDayGenerationService {

    AiTripDayGeneration getLatest(String sessionId, Integer dayNo);

    AiTripDayGeneration createPending(
            String sessionId, Long userId, Integer dayNo, Integer generationVersion, String requestMode);

    AiTripDayGeneration createQueuedIfAbsent(
            String sessionId, Long userId, Integer dayNo, String requestMode);

    void markQueued(Long id);

    void markGenerating(Long id);

    void markGenerated(Long id, String resultJson);

    void markFailed(Long id, String errorMessage);

    void supersedeDay(String sessionId, Integer dayNo);

    void switchCurrentVersion(String sessionId, Integer dayNo, Integer generationVersion);
}
