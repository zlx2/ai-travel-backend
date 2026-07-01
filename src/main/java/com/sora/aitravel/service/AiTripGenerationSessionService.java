package com.sora.aitravel.service;

import com.sora.aitravel.entity.AiTripGenerationSession;

/** AI 行程生成会话服务。 */
public interface AiTripGenerationSessionService {

    AiTripGenerationSession createPreparing(
            Long userId,
            String conversationId,
            String requirementJson,
            String selectedQuoteJson,
            String rentalTripContextJson);

    AiTripGenerationSession getBySessionId(String sessionId);

    void updateRequirementJson(String sessionId, String requirementJson);

    void markPrepared(
            String sessionId,
            String daySkeletonsJson,
            String cityProfileJson,
            String weatherJson,
            String hotelJson);

    void markFailed(String sessionId, String errorMessage);
}
