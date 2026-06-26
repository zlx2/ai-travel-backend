package com.sora.aitravel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sora.aitravel.entity.AiTripDayGeneration;
import com.sora.aitravel.mapper.AiTripDayGenerationMapper;
import com.sora.aitravel.service.AiTripDayGenerationService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** AI 行程按天生成服务实现。 */
@Service
@RequiredArgsConstructor
public class AiTripDayGenerationServiceImpl implements AiTripDayGenerationService {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_QUEUED = "QUEUED";
    public static final String STATUS_GENERATING = "GENERATING";
    public static final String STATUS_GENERATED = "GENERATED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_SUPERSEDED = "SUPERSEDED";

    private final AiTripDayGenerationMapper mapper;

    @Override
    public AiTripDayGeneration getLatest(String sessionId, Integer dayNo) {
        return mapper.selectOne(
                new LambdaQueryWrapper<AiTripDayGeneration>()
                        .eq(AiTripDayGeneration::getSessionId, sessionId)
                        .eq(AiTripDayGeneration::getDayNo, dayNo)
                        .orderByDesc(AiTripDayGeneration::getGenerationVersion)
                        .last("LIMIT 1"));
    }

    @Override
    public AiTripDayGeneration createPending(
            String sessionId, Long userId, Integer dayNo, Integer generationVersion, String requestMode) {
        AiTripDayGeneration day =
                AiTripDayGeneration.builder()
                        .sessionId(sessionId)
                        .userId(userId)
                        .dayNo(dayNo)
                        .generationVersion(generationVersion)
                        .status(STATUS_PENDING)
                        .isCurrent(1)
                        .requestMode(requestMode)
                        .build();
        mapper.insert(day);
        return day;
    }

    @Override
    public void markQueued(Long id) {
        updateStatus(id, STATUS_QUEUED, null, null);
    }

    @Override
    public void markGenerating(Long id) {
        updateStatus(id, STATUS_GENERATING, LocalDateTime.now(), null);
    }

    @Override
    public void markGenerated(Long id, String resultJson) {
        mapper.update(
                null,
                new LambdaUpdateWrapper<AiTripDayGeneration>()
                        .eq(AiTripDayGeneration::getId, id)
                        .set(AiTripDayGeneration::getStatus, STATUS_GENERATED)
                        .set(AiTripDayGeneration::getResultJson, resultJson)
                        .set(AiTripDayGeneration::getErrorMessage, null)
                        .set(AiTripDayGeneration::getFinishedAt, LocalDateTime.now()));
    }

    @Override
    public void markFailed(Long id, String errorMessage) {
        mapper.update(
                null,
                new LambdaUpdateWrapper<AiTripDayGeneration>()
                        .eq(AiTripDayGeneration::getId, id)
                        .set(AiTripDayGeneration::getStatus, STATUS_FAILED)
                        .set(AiTripDayGeneration::getErrorMessage, trimError(errorMessage))
                        .set(AiTripDayGeneration::getFinishedAt, LocalDateTime.now()));
    }

    @Override
    public void supersedeDay(String sessionId, Integer dayNo) {
        mapper.update(
                null,
                new LambdaUpdateWrapper<AiTripDayGeneration>()
                        .eq(AiTripDayGeneration::getSessionId, sessionId)
                        .eq(AiTripDayGeneration::getDayNo, dayNo)
                        .ne(AiTripDayGeneration::getStatus, STATUS_SUPERSEDED)
                        .set(AiTripDayGeneration::getIsCurrent, 0)
                        .set(AiTripDayGeneration::getStatus, STATUS_SUPERSEDED));
    }

    @Override
    public void switchCurrentVersion(String sessionId, Integer dayNo, Integer generationVersion) {
        mapper.update(
                null,
                new LambdaUpdateWrapper<AiTripDayGeneration>()
                        .eq(AiTripDayGeneration::getSessionId, sessionId)
                        .eq(AiTripDayGeneration::getDayNo, dayNo)
                        .set(AiTripDayGeneration::getIsCurrent, 0));
        mapper.update(
                null,
                new LambdaUpdateWrapper<AiTripDayGeneration>()
                        .eq(AiTripDayGeneration::getSessionId, sessionId)
                        .eq(AiTripDayGeneration::getDayNo, dayNo)
                        .eq(AiTripDayGeneration::getGenerationVersion, generationVersion)
                        .set(AiTripDayGeneration::getIsCurrent, 1));
    }

    private void updateStatus(Long id, String status, LocalDateTime startedAt, LocalDateTime finishedAt) {
        LambdaUpdateWrapper<AiTripDayGeneration> wrapper =
                new LambdaUpdateWrapper<AiTripDayGeneration>()
                        .eq(AiTripDayGeneration::getId, id)
                        .set(AiTripDayGeneration::getStatus, status)
                        .set(AiTripDayGeneration::getErrorMessage, null);
        if (startedAt != null) {
            wrapper.set(AiTripDayGeneration::getStartedAt, startedAt);
        }
        if (finishedAt != null) {
            wrapper.set(AiTripDayGeneration::getFinishedAt, finishedAt);
        }
        mapper.update(null, wrapper);
    }

    private String trimError(String errorMessage) {
        if (errorMessage == null) {
            return null;
        }
        return errorMessage.length() > 1000 ? errorMessage.substring(0, 1000) : errorMessage;
    }
}
