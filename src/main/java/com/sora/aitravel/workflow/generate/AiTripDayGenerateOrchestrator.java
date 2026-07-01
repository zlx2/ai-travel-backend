package com.sora.aitravel.workflow.generate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.dto.model.RentalQuoteOptionDTO;
import com.sora.aitravel.dto.model.RentalTripContextDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.model.TripPlanDTO;
import com.sora.aitravel.entity.AiTripDayGeneration;
import com.sora.aitravel.entity.AiTripGenerationSession;
import com.sora.aitravel.service.AiTripDayGenerateService;
import com.sora.aitravel.service.AiTripDayGenerationService;
import com.sora.aitravel.service.AiTripGenerationSessionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 单日行程生成编排器：负责按天查询、选点、排序、组装 timeline 和持久化。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiTripDayGenerateOrchestrator implements AiTripDayGenerateService {

    private static final TypeReference<List<DaySkeleton>> DAY_SKELETON_LIST =
            new TypeReference<>() {};

    private final AiTripGenerationSessionService sessionService;
    private final AiTripDayGenerationService dayGenerationService;
    private final TripDayGenerateWorkflow tripDayGenerateWorkflow;
    private final ObjectMapper objectMapper;

    /**
     * 生成指定日期的行程计划。
     *
     * <p>整体流程：幂等检查 → 创建/复用生成记录 → 执行工作流节点 → 持久化结果。
     * 支持强制重新生成（forceRegenerate=true）和异步复用（ASYNC模式）两种策略。</p>
     *
     * @param sessionId       生成会话ID
     * @param dayNo           第几天（从1开始）
     * @param requestMode     请求模式，如 "SYNC"（同步）、"ASYNC"（异步）
     * @param forceRegenerate 是否强制重新生成，忽略已有结果
     * @return 生成记录（包含状态和结果JSON）
     */
    @Override
    public AiTripDayGeneration generateDay(
            String sessionId, Integer dayNo, String requestMode, boolean forceRegenerate) {
        return generateDay(sessionId, dayNo, requestMode, forceRegenerate, null);
    }

    @Override
    public AiTripDayGeneration generateDay(
            String sessionId,
            Integer dayNo,
            String requestMode,
            boolean forceRegenerate,
            String revisionText) {
        // 1. 校验会话状态：必须为已准备（PREPARED）状态才能生成行程
        AiTripGenerationSession session = requirePreparedSession(sessionId);
        AiTripDayGeneration latest = dayGenerationService.getLatest(sessionId, dayNo);

        // 2. 快速返回：已生成完成且非强制重新生成 → 直接返回已有结果
        if (!forceRegenerate && latest != null && "GENERATED".equals(latest.getStatus())) {
            return latest;
        }

        // 3. 进行中的幂等保护：正在排队或生成中时，
        //    仅当「排队中 + ASYNC模式」才允许继续（复用该记录），其余情况直接返回
        if (!forceRegenerate
                && latest != null
                && ("QUEUED".equals(latest.getStatus())
                        || "GENERATING".equals(latest.getStatus()))) {
            if (!"QUEUED".equals(latest.getStatus()) || !"ASYNC".equals(requestMode)) {
                return latest;
            }
        }

        // 4. 确定生成记录：复用 or 新建
        AiTripDayGeneration day;
        if (!forceRegenerate
                && latest != null
                && "QUEUED".equals(latest.getStatus())
                && "ASYNC".equals(requestMode)) {
            // ASYNC模式下，复用已有的QUEUED记录，避免重复创建
            day = latest;
        } else {
            // 强制重新生成 或 旧记录为失败/已废弃状态 → 版本递增，创建新记录
            int version = latest == null ? 1 : latest.getGenerationVersion() + 1;
            if (latest != null) {
                // 将旧记录标记为已废弃（is_current=0）
                dayGenerationService.supersedeDay(sessionId, dayNo);
            }
            day =
                    dayGenerationService.createPending(
                            sessionId, session.getUserId(), dayNo, version, requestMode);
        }

        // 5. 执行生成流程：恢复上下文 → 运行工作流节点 → 持久化结果
        long start = WorkflowTiming.start();
        try {
            timed("day-mark-generating", () -> dayGenerationService.markGenerating(day.getId()));
            // 从会话持久化数据中恢复工作流上下文（需求、城市、天气、酒店、前几天行程）
            GenerateWorkflowContext context = timed("restore-context", () -> restoreContext(session, dayNo));
            context.setRevisionText(normalizeRevisionText(revisionText));
            context.setTargetDayNo(dayNo);
            GenerateWorkflowContext workflowInput = context;
            context = timed("trip-day-generate-workflow", () -> tripDayGenerateWorkflow.execute(workflowInput));
            TripPlanDTO.DailyPlan generatedPlan = currentGeneratedPlan(context, dayNo);
            // 生成成功，将第一天（即当前dayNo）的计划JSON写入结果
            timed(
                    "day-mark-generated",
                    () ->
                            dayGenerationService.markGenerated(
                                    day.getId(), writeJson(generatedPlan)));
            AiTripDayGeneration generated =
                    timed("day-load-generated", () -> dayGenerationService.getLatest(sessionId, dayNo));
            log.info(
                    "行程生成总耗时 workflow=generate-day sessionId={} day={} elapsedMs={}",
                    sessionId,
                    dayNo,
                    WorkflowTiming.elapsedMs(start));
            return generated;
        } catch (RuntimeException exception) {
            // 生成失败，记录错误信息并向上抛出
            dayGenerationService.markFailed(day.getId(), exception.getMessage());
            log.info(
                    "行程生成总耗时 workflow=generate-day sessionId={} day={} status=failed elapsedMs={}",
                    sessionId,
                    dayNo,
                    WorkflowTiming.elapsedMs(start));
            throw exception;
        }
    }

    private String normalizeRevisionText(String revisionText) {
        if (revisionText == null || revisionText.isBlank()) {
            return null;
        }
        String text = revisionText.trim().replaceAll("\\s+", " ");
        return text.length() > 500 ? text.substring(0, 500) : text;
    }

    private TripPlanDTO.DailyPlan currentGeneratedPlan(GenerateWorkflowContext context, Integer dayNo) {
        if (context.getLockedDailyPlans() == null || context.getLockedDailyPlans().isEmpty()) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "单日行程生成结果为空，day=" + dayNo);
        }
        return context.getLockedDailyPlans().stream()
                .filter(plan -> dayNo.equals(plan.getDay()))
                .findFirst()
                .orElseGet(() -> {
                    if (context.getLockedDailyPlans().size() == 1) {
                        return context.getLockedDailyPlans().get(0);
                    }
                    throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "未找到当前天行程结果，day=" + dayNo);
                });
    }

    /**
     * 获取已准备好的生成会话。
     * @param sessionId
     * @return
     */
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

    /**
     * 恢复生成工作流上下文。
     * @param session
     * @param dayNo
     * @return
     */
    private GenerateWorkflowContext restoreContext(AiTripGenerationSession session, Integer dayNo) {
        return GenerateWorkflowContext.builder()
                .userId(session.getUserId())
                .requirement(read(session.getRequirementJson(), TravelRequirementDTO.class))
                .selectedQuote(readNullable(session.getSelectedQuoteJson(), RentalQuoteOptionDTO.class))
                .rentalTripContext(readNullable(session.getRentalTripContextJson(), RentalTripContextDTO.class))
                .daySkeletons(read(session.getDaySkeletonsJson(), DAY_SKELETON_LIST))
                .cityProfile(read(session.getCityProfileJson(), CityProfile.class))
                .weatherForecast(session.getWeatherJson())
                .hotelSearchResult(session.getHotelJson())
                .lockedDailyPlans(readGeneratedPreviousDays(session.getSessionId(), dayNo))
                .singleDayGeneration(true)
                .build();
    }

    /**
     * 读取已生成的前一天行程数据。
     * @param sessionId
     * @param dayNo
     * @return
     */
    private List<TripPlanDTO.DailyPlan> readGeneratedPreviousDays(String sessionId, Integer dayNo) {
        return dayGenerationService.listCurrentGeneratedBefore(sessionId, dayNo).stream()
                .map(this::readGeneratedDay)
                .toList();
    }

    /**
     * 读取已生成的单日行程数据。
     * @param day
     * @return
     */
    private TripPlanDTO.DailyPlan readGeneratedDay(AiTripDayGeneration day) {
        try {
            return objectMapper.readValue(day.getResultJson(), TripPlanDTO.DailyPlan.class);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "已生成单日行程数据解析失败");
        }
    }

    private void timed(String node, Runnable action) {
        WorkflowTiming.run("generate-day", node, action);
    }

    private <T> T timed(String node, java.util.function.Supplier<T> action) {
        return WorkflowTiming.call("generate-day", node, action);
    }

    /**
     * 从JSON字符串读取数据。
     * @param json
     * @param type
     * @param <T>
     * @return
     */
    private <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "生成会话数据解析失败");
        }
    }

    private <T> T readNullable(String json, Class<T> type) {
        if (json == null || json.isBlank() || "null".equals(json)) {
            return null;
        }
        return read(json, type);
    }

    /**
     * 从JSON字符串读取数据。
     * @param json
     * @param type
     * @param <T>
     * @return
     */
    private <T> T read(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "生成会话数据解析失败");
        }
    }

    /**
     * 将数据写入JSON字符串。
     * @param value
     * @return
     */
    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "单日生成数据序列化失败");
        }
    }
}
