package com.sora.aitravel.service;

import com.sora.aitravel.entity.AiTripDayGeneration;

/** AI 行程单日生成编排服务。 */
public interface AiTripDayGenerateService {

    AiTripDayGeneration generateDay(
            String sessionId, Integer dayNo, String requestMode, boolean forceRegenerate);
}
