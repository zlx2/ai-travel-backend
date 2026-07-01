package com.sora.aitravel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sora.aitravel.entity.AiTripGenerationSession;
import com.sora.aitravel.mapper.AiTripGenerationSessionMapper;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** AI 行程生成会话服务实现。 */
@Service
@RequiredArgsConstructor
public class AiTripGenerationSessionServiceImpl {

    public static final String STATUS_PREPARING = "PREPARING";
    public static final String STATUS_PREPARED = "PREPARED";
    public static final String STATUS_FAILED = "FAILED";

    private final AiTripGenerationSessionMapper mapper;

    public AiTripGenerationSession createPreparing(
            Long userId,
            String conversationId,
            String requirementJson,
            String selectedQuoteJson,
            String rentalTripContextJson) {
        AiTripGenerationSession session =
                AiTripGenerationSession.builder()
                        .sessionId(UUID.randomUUID().toString())
                        .userId(userId)
                        .conversationId(conversationId)
                        .requirementJson(requirementJson)
                        .selectedQuoteJson(selectedQuoteJson)
                        .rentalTripContextJson(rentalTripContextJson)
                        .daySkeletonsJson("[]")
                        .status(STATUS_PREPARING)
                        .build();
        mapper.insert(session);
        return session;
    }

    public AiTripGenerationSession getBySessionId(String sessionId) {
        return mapper.selectOne(
                new LambdaQueryWrapper<AiTripGenerationSession>()
                        .eq(AiTripGenerationSession::getSessionId, sessionId));
    }

    public void updateRequirementJson(String sessionId, String requirementJson) {
        mapper.update(
                null,
                new LambdaUpdateWrapper<AiTripGenerationSession>()
                        .eq(AiTripGenerationSession::getSessionId, sessionId)
                        .set(AiTripGenerationSession::getRequirementJson, requirementJson));
    }

    public void markPrepared(
            String sessionId,
            String daySkeletonsJson,
            String cityProfileJson,
            String weatherJson,
            String hotelJson) {
        mapper.update(
                null,
                new LambdaUpdateWrapper<AiTripGenerationSession>()
                        .eq(AiTripGenerationSession::getSessionId, sessionId)
                        .set(AiTripGenerationSession::getDaySkeletonsJson, daySkeletonsJson)
                        .set(AiTripGenerationSession::getCityProfileJson, cityProfileJson)
                        .set(AiTripGenerationSession::getWeatherJson, weatherJson)
                        .set(AiTripGenerationSession::getHotelJson, hotelJson)
                        .set(AiTripGenerationSession::getStatus, STATUS_PREPARED)
                        .set(AiTripGenerationSession::getErrorMessage, null));
    }

    public void markFailed(String sessionId, String errorMessage) {
        mapper.update(
                null,
                new LambdaUpdateWrapper<AiTripGenerationSession>()
                        .eq(AiTripGenerationSession::getSessionId, sessionId)
                        .set(AiTripGenerationSession::getStatus, STATUS_FAILED)
                        .set(AiTripGenerationSession::getErrorMessage, trimError(errorMessage)));
    }

    private String trimError(String errorMessage) {
        if (errorMessage == null) {
            return null;
        }
        return errorMessage.length() > 1000 ? errorMessage.substring(0, 1000) : errorMessage;
    }
}
