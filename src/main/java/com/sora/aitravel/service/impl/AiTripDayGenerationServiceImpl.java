package com.sora.aitravel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sora.aitravel.entity.AiTripDayGeneration;
import com.sora.aitravel.mapper.AiTripDayGenerationMapper;
import com.sora.aitravel.service.AiTripDayGenerationService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** AI 行程按天生成服务实现。 */
@Service
@RequiredArgsConstructor
public class AiTripDayGenerationServiceImpl implements AiTripDayGenerationService {

    public static final String STATUS_PENDING = "PENDING"; // 待生成
    public static final String STATUS_QUEUED = "QUEUED"; // 排队中
    public static final String STATUS_GENERATING = "GENERATING"; // 生成中
    public static final String STATUS_GENERATED = "GENERATED"; // 生成成功
    public static final String STATUS_FAILED = "FAILED"; // 生成失败
    public static final String STATUS_SUPERSEDED = "SUPERSEDED"; // 被覆盖

    private final AiTripDayGenerationMapper mapper;

    /**
     * 获取某天最新一次版本的行程
     *
     * @param sessionId
     * @param dayNo
     * @return
     */
    @Override
    public AiTripDayGeneration getLatest(String sessionId, Integer dayNo) {
        return mapper.selectOne(
                new LambdaQueryWrapper<AiTripDayGeneration>()
                        .eq(AiTripDayGeneration::getSessionId, sessionId)
                        .eq(AiTripDayGeneration::getDayNo, dayNo)
                        .orderByDesc(AiTripDayGeneration::getGenerationVersion)
                        .last("LIMIT 1"));
    }

    /**
     * 查询已生成且当前生效的行程日数
     *
     * @param sessionId
     * @param dayNo
     * @return
     */
    @Override
    public List<AiTripDayGeneration> listCurrentGeneratedBefore(String sessionId, Integer dayNo) {
        return mapper.selectList(
                new LambdaQueryWrapper<AiTripDayGeneration>()
                        .eq(AiTripDayGeneration::getSessionId, sessionId)
                        .eq(AiTripDayGeneration::getIsCurrent, 1)
                        .eq(AiTripDayGeneration::getStatus, STATUS_GENERATED)
                        .lt(AiTripDayGeneration::getDayNo, dayNo)
                        .orderByAsc(AiTripDayGeneration::getDayNo));
    }

    /**
     * 创建待生成的行程
     *
     * @param sessionId
     * @param userId
     * @param dayNo
     * @param generationVersion
     * @param requestMode
     * @return
     */
    @Override
    public AiTripDayGeneration createPending(
            String sessionId,
            Long userId,
            Integer dayNo,
            Integer generationVersion,
            String requestMode) {
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

    /**
     * 创建为指定行程会话的某天创建排队中的生成记录
     *
     * @param sessionId
     * @param userId
     * @param dayNo
     * @param requestMode
     * @return
     */
    @Override
    public AiTripDayGeneration createQueuedIfAbsent(
            String sessionId, Long userId, Integer dayNo, String requestMode) {
        AiTripDayGeneration latest = getLatest(sessionId, dayNo);
        if (latest != null
                && (STATUS_GENERATED.equals(latest.getStatus())
                        || STATUS_GENERATING.equals(latest.getStatus())
                        || STATUS_QUEUED.equals(latest.getStatus()))) {
            return latest;
        }
        int version = latest == null ? 1 : latest.getGenerationVersion() + 1;
        if (latest != null) {
            supersedeDay(sessionId, dayNo);
        }
        AiTripDayGeneration day =
                AiTripDayGeneration.builder()
                        .sessionId(sessionId)
                        .userId(userId)
                        .dayNo(dayNo)
                        .generationVersion(version)
                        .status(STATUS_QUEUED)
                        .isCurrent(1)
                        .requestMode(requestMode)
                        .build();
        mapper.insert(day);
        return day;
    }

    /**
     * 状态为等待中
     *
     * @param id
     */
    @Override
    public void markQueued(Long id) {
        updateStatus(id, STATUS_QUEUED, null, null);
    }

    /**
     * 修改状态为生成中
     *
     * @param id
     */
    @Override
    public void markGenerating(Long id) {
        updateStatus(id, STATUS_GENERATING, LocalDateTime.now(), null);
    }

    /**
     * 修改状态为生成成功
     *
     * @param id
     * @param resultJson
     */
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

    /**
     * 更新状态为生成失败
     *
     * @param id
     * @param errorMessage
     */
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

    /**
     * 更新状态为过期
     *
     * @param sessionId
     * @param dayNo
     */
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

    /**
     * 更新状态为切换当前版本为指定版本
     *
     * @param sessionId
     * @param dayNo
     * @param generationVersion
     */
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

    /**
     * 更新状态
     *
     * @param id
     * @param status
     * @param startedAt
     * @param finishedAt
     */
    private void updateStatus(
            Long id, String status, LocalDateTime startedAt, LocalDateTime finishedAt) {
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

    /**
     * 去除错误信息中的换行符
     *
     * @param errorMessage
     * @return
     */
    private String trimError(String errorMessage) {
        if (errorMessage == null) {
            return null;
        }
        return errorMessage.length() > 1000 ? errorMessage.substring(0, 1000) : errorMessage;
    }
}
