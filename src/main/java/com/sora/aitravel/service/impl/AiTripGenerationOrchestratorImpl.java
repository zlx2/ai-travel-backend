package com.sora.aitravel.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.dto.request.TripGenerateRequest;
import com.sora.aitravel.entity.AiTripGenerationSession;
import com.sora.aitravel.service.AiTripGenerationOrchestrator;
import com.sora.aitravel.service.AiTripGenerationSessionService;
import com.sora.aitravel.workflow.generate.CityDataProfileNode;
import com.sora.aitravel.workflow.generate.DayStateInitNode;
import com.sora.aitravel.workflow.generate.GenerateWorkflowContext;
import com.sora.aitravel.workflow.generate.HotelFetchNode;
import com.sora.aitravel.workflow.generate.RequirementLoadNode;
import com.sora.aitravel.workflow.generate.RequirementValidateNode;
import com.sora.aitravel.workflow.generate.RouteScopeResolveNode;
import com.sora.aitravel.workflow.generate.TripSkeletonNode;
import com.sora.aitravel.workflow.generate.WeatherFetchNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** AI 行程生成编排服务实现。 */
@Service
@RequiredArgsConstructor
public class AiTripGenerationOrchestratorImpl implements AiTripGenerationOrchestrator {

    private final AiTripGenerationSessionService sessionService;
    private final RequirementValidateNode requirementValidateNode;
    private final RequirementLoadNode requirementLoadNode;
    private final RouteScopeResolveNode routeScopeResolveNode;
    private final TripSkeletonNode tripSkeletonNode;
    private final CityDataProfileNode cityDataProfileNode;
    private final WeatherFetchNode weatherFetchNode;
    private final HotelFetchNode hotelFetchNode;
    private final DayStateInitNode dayStateInitNode;
    private final ObjectMapper objectMapper;

    @Override
    public AiTripGenerationSession prepareSession(Long userId, TripGenerateRequest request) {
        AiTripGenerationSession session =
                sessionService.createPreparing(
                        userId, request.getConversationId(), writeJson(request.getRequirement()));
        GenerateWorkflowContext context = new GenerateWorkflowContext();
        context.setUserId(userId);
        context.setRequest(request);
        try {
            requirementValidateNode.execute(context);
            requirementLoadNode.execute(context);
            routeScopeResolveNode.execute(context);
            sessionService.updateRequirementJson(
                    session.getSessionId(), writeJson(context.getRequirement()));
            tripSkeletonNode.execute(context);
            cityDataProfileNode.execute(context);
            validatePreparedContext(context);
            weatherFetchNode.execute(context);
            hotelFetchNode.execute(context);
            dayStateInitNode.execute(context);
            sessionService.markPrepared(
                    session.getSessionId(),
                    writeJson(context.getDaySkeletons()),
                    writeJson(context.getCityProfile()),
                    writeJson(context.getWeatherForecast()),
                    writeJson(context.getHotelSearchResult()));
            return sessionService.getBySessionId(session.getSessionId());
        } catch (RuntimeException exception) {
            sessionService.markFailed(session.getSessionId(), exception.getMessage());
            throw exception;
        }
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
}
