package com.sora.aitravel.workflow.generate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.entity.AiTripDayGeneration;
import com.sora.aitravel.entity.AiTripGenerationSession;
import com.sora.aitravel.service.AiTripDayGenerationService;
import com.sora.aitravel.service.AiTripDayGenerateService;
import com.sora.aitravel.service.AiTripGenerationSessionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 按天生成行程编排。 */
@Service
@RequiredArgsConstructor
public class AiTripDayGenerateOrchestrator implements AiTripDayGenerateService {

    private static final TypeReference<List<DaySkeleton>> DAY_SKELETON_LIST =
            new TypeReference<>() {};

    private final AiTripGenerationSessionService sessionService;
    private final AiTripDayGenerationService dayGenerationService;
    private final DayContextBuildNode dayContextBuildNode;
    private final DayQueryPlanNode dayQueryPlanNode;
    private final FoodRecommendNode foodRecommendNode;
    private final DayDataFetchNode dayDataFetchNode;
    private final DayDataRankNode dayDataRankNode;
    private final DayPlanGenerateNode dayPlanGenerateNode;
    private final DayPlanValidateNode dayPlanValidateNode;
    private final ObjectMapper objectMapper;

    @Override
    public AiTripDayGeneration generateDay(
            String sessionId, Integer dayNo, String requestMode, boolean forceRegenerate) {
        AiTripGenerationSession session = requirePreparedSession(sessionId);
        AiTripDayGeneration latest = dayGenerationService.getLatest(sessionId, dayNo);
        if (!forceRegenerate
                && latest != null
                && "GENERATED".equals(latest.getStatus())) {
            return latest;
        }
        if (!forceRegenerate
                && latest != null
                && ("QUEUED".equals(latest.getStatus()) || "GENERATING".equals(latest.getStatus()))) {
            if (!"QUEUED".equals(latest.getStatus()) || !"ASYNC".equals(requestMode)) {
                return latest;
            }
        }
        AiTripDayGeneration day;
        if (!forceRegenerate
                && latest != null
                && "QUEUED".equals(latest.getStatus())
                && "ASYNC".equals(requestMode)) {
            day = latest;
        } else {
            int version = latest == null ? 1 : latest.getGenerationVersion() + 1;
            if (latest != null) {
                dayGenerationService.supersedeDay(sessionId, dayNo);
            }
            day =
                    dayGenerationService.createPending(
                            sessionId, session.getUserId(), dayNo, version, requestMode);
        }
        try {
            dayGenerationService.markGenerating(day.getId());
            GenerateWorkflowContext context = restoreContext(session);
            runDayNodes(context, dayNo);
            dayGenerationService.markGenerated(
                    day.getId(),
                    writeJson(context.getLockedDailyPlans().get(0)));
            return dayGenerationService.getLatest(sessionId, dayNo);
        } catch (RuntimeException exception) {
            dayGenerationService.markFailed(day.getId(), exception.getMessage());
            throw exception;
        }
    }

    private AiTripGenerationSession requirePreparedSession(String sessionId) {
        AiTripGenerationSession session = sessionService.getBySessionId(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "生成会话不存在");
        }
        if (!"PREPARED".equals(session.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "生成会话未准备完成");
        }
        return session;
    }

    private GenerateWorkflowContext restoreContext(AiTripGenerationSession session) {
        GenerateWorkflowContext context = new GenerateWorkflowContext();
        context.setUserId(session.getUserId());
        context.setRequirement(read(session.getRequirementJson(), TravelRequirementDTO.class));
        context.setDaySkeletons(read(session.getDaySkeletonsJson(), DAY_SKELETON_LIST));
        context.setCityProfile(read(session.getCityProfileJson(), CityProfile.class));
        context.setWeatherForecast(read(session.getWeatherJson(), String.class));
        context.setHotelSearchResult(read(session.getHotelJson(), String.class));
        context.setLockedDailyPlans(new java.util.ArrayList<>());
        context.setSingleDayGeneration(true);
        return context;
    }

    private void runDayNodes(GenerateWorkflowContext context, Integer dayNo) {
        dayContextBuildNode.execute(context);
        context.setDayContexts(
                context.getDayContexts().stream()
                        .filter(dayContext -> dayContext.getDay().equals(dayNo))
                        .toList());
        if (context.getDayContexts().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "行程天数不存在：" + dayNo);
        }
        dayQueryPlanNode.execute(context);
        foodRecommendNode.execute(context);
        dayDataFetchNode.execute(context);
        dayDataRankNode.execute(context);
        dayPlanGenerateNode.execute(context);
        dayPlanValidateNode.execute(context);
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "生成会话数据解析失败");
        }
    }

    private <T> T read(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "生成会话数据解析失败");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "单日生成数据序列化失败");
        }
    }
}
