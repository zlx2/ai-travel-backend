package com.sora.aitravel.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.dto.request.TripGenerateRequest;
import com.sora.aitravel.entity.AiTripGenerationSession;
import com.sora.aitravel.service.AiTripGenerationOrchestrator;
import com.sora.aitravel.service.AiTripGenerationSessionService;
import com.sora.aitravel.workflow.generate.GenerateWorkflowContext;
import com.sora.aitravel.workflow.generate.TripPrepareWorkflow;
import com.sora.aitravel.workflow.generate.WorkflowTiming;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 行程生成准备阶段编排器：负责需求、城市资料、宏观路线、天气酒店和会话初始化。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiTripGenerationOrchestratorImpl implements AiTripGenerationOrchestrator {

    private final AiTripGenerationSessionService sessionService;
    private final TripPrepareWorkflow tripPrepareWorkflow;
    private final ObjectMapper objectMapper;

    @Override
    public AiTripGenerationSession prepareSession(Long userId, TripGenerateRequest request) {
        AiTripGenerationSession session =
                sessionService.createPreparing(
                        userId,
                        request.getConversationId(),
                        writeJson(request.getRequirement()),
                        writeJsonOrNull(request.getSelectedQuote()),
                        writeJsonOrNull(request.getRentalTripContext()));
        GenerateWorkflowContext context = new GenerateWorkflowContext();
        context.setUserId(userId);
        context.setRequest(request);
        long start = WorkflowTiming.start();
        try {
            timed("trip-prepare-workflow", () -> tripPrepareWorkflow.execute(context));
            timed(
                    "session-requirement-update",
                    () ->
                            sessionService.updateRequirementJson(
                                    session.getSessionId(), writeJson(context.getRequirement())));
            timed(
                    "session-mark-prepared",
                    () ->
                            sessionService.markPrepared(
                                    session.getSessionId(),
                                    writeJson(context.getDaySkeletons()),
                                    writeJson(context.getCityProfile()),
                                    writeJson(context.getWeatherForecast()),
                                    writeJson(context.getHotelSearchResult())));
            AiTripGenerationSession prepared =
                    timed(
                            "session-load-prepared",
                            () -> sessionService.getBySessionId(session.getSessionId()));
            log.info(
                    "行程生成总耗时 workflow=prepare-session sessionId={} elapsedMs={}",
                    session.getSessionId(),
                    WorkflowTiming.elapsedMs(start));
            return prepared;
        } catch (RuntimeException exception) {
            sessionService.markFailed(session.getSessionId(), exception.getMessage());
            log.info(
                    "行程生成总耗时 workflow=prepare-session sessionId={} status=failed elapsedMs={}",
                    session.getSessionId(),
                    WorkflowTiming.elapsedMs(start));
            throw exception;
        }
    }

    private void timed(String node, Runnable action) {
        WorkflowTiming.run("prepare-session", node, action);
    }

    private <T> T timed(String node, java.util.function.Supplier<T> action) {
        return WorkflowTiming.call("prepare-session", node, action);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "行程生成数据序列化失败");
        }
    }

    private String writeJsonOrNull(Object value) {
        return value == null ? null : writeJson(value);
    }
}
