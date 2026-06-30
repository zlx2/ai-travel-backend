package com.sora.aitravel.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.dto.request.TripGenerateRequest;
import com.sora.aitravel.entity.AiTripGenerationSession;
import com.sora.aitravel.service.AiTripGenerationOrchestrator;
import com.sora.aitravel.service.AiTripGenerationSessionService;
import com.sora.aitravel.workflow.generate.AiMacroRoutePlanNode;
import com.sora.aitravel.workflow.generate.AiRouteCriticNode;
import com.sora.aitravel.workflow.generate.AmapMacroRouteFactNode;
import com.sora.aitravel.workflow.generate.CandidatePoolBuildNode;
import com.sora.aitravel.workflow.generate.CityDataProfileNode;
import com.sora.aitravel.workflow.generate.DayStateInitNode;
import com.sora.aitravel.workflow.generate.GenerateWorkflowContext;
import com.sora.aitravel.workflow.generate.HotelFetchNode;
import com.sora.aitravel.workflow.generate.MacroRouteContractValidateNode;
import com.sora.aitravel.workflow.generate.RequirementLoadNode;
import com.sora.aitravel.workflow.generate.RequirementValidateNode;
import com.sora.aitravel.workflow.generate.RouteScopeResolveNode;
import com.sora.aitravel.workflow.generate.WeatherFetchNode;
import com.sora.aitravel.workflow.generate.WorkflowTiming;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** AI 行程生成编排服务实现。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiTripGenerationOrchestratorImpl implements AiTripGenerationOrchestrator {

    private final AiTripGenerationSessionService sessionService;
    private final RequirementValidateNode requirementValidateNode;
    private final RequirementLoadNode requirementLoadNode;
    private final RouteScopeResolveNode routeScopeResolveNode;
    private final CityDataProfileNode cityDataProfileNode;
    private final CandidatePoolBuildNode candidatePoolBuildNode;
    private final AiMacroRoutePlanNode aiMacroRoutePlanNode;
    private final AmapMacroRouteFactNode amapMacroRouteFactNode;
    private final AiRouteCriticNode aiRouteCriticNode;
    private final MacroRouteContractValidateNode macroRouteContractValidateNode;
    private final WeatherFetchNode weatherFetchNode;
    private final HotelFetchNode hotelFetchNode;
    private final DayStateInitNode dayStateInitNode;
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
            timed("requirement-validate", () -> requirementValidateNode.execute(context));
            timed("requirement-load", () -> requirementLoadNode.execute(context));
            timed("route-scope-resolve", () -> routeScopeResolveNode.execute(context));
            timed(
                    "session-requirement-update",
                    () ->
                            sessionService.updateRequirementJson(
                                    session.getSessionId(), writeJson(context.getRequirement())));
            timed("city-data-profile", () -> cityDataProfileNode.execute(context));
            timed("candidate-pool-build", () -> candidatePoolBuildNode.execute(context));
            timed("ai-macro-route-plan", () -> aiMacroRoutePlanNode.execute(context));
            timed("amap-macro-route-fact", () -> amapMacroRouteFactNode.execute(context));
            timed("ai-route-critic", () -> aiRouteCriticNode.execute(context));
            timed(
                    "macro-route-contract-validate",
                    () -> macroRouteContractValidateNode.execute(context));
            timed("prepared-context-validate", () -> validatePreparedContext(context));
            timed("weather-fetch", () -> weatherFetchNode.execute(context));
            timed("hotel-fetch", () -> hotelFetchNode.execute(context));
            timed("day-state-init", () -> dayStateInitNode.execute(context));
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

    private void validatePreparedContext(GenerateWorkflowContext context) {
        int days = context.getRequirement().getDays();
        if (context.getDaySkeletons() == null || context.getDaySkeletons().size() != days) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "行程骨架数量与天数不一致");
        }
        if (!context.hasScenicCandidates()) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "目的地景点候选为空");
        }
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
