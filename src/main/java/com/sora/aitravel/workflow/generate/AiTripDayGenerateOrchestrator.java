package com.sora.aitravel.workflow.generate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.common.utils.JsonCodec;
import com.sora.aitravel.common.utils.WorkflowTiming;
import com.sora.aitravel.dto.model.RentalQuoteOptionDTO;
import com.sora.aitravel.dto.model.RentalTripContextDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.model.TripPlanDTO;
import com.sora.aitravel.dto.workflow.generate.DayGenerateInput;
import com.sora.aitravel.dto.workflow.generate.DayGenerateResult;
import com.sora.aitravel.entity.AiTripDayGeneration;
import com.sora.aitravel.entity.AiTripGenerationSession;
import com.sora.aitravel.model.CityProfile;
import com.sora.aitravel.model.DaySkeleton;
import com.sora.aitravel.service.impl.AiTripDayGenerationServiceImpl;
import com.sora.aitravel.service.impl.AiTripGenerationSessionServiceImpl;
import com.sora.aitravel.service.impl.NearbyHotelService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 单日行程生成编排器：负责按天查询、选点、排序、组装 timeline 和持久化。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiTripDayGenerateOrchestrator {

    private static final int MAX_WORKFLOW_ATTEMPTS = 3;
    private static final TypeReference<List<DaySkeleton>> DAY_SKELETON_LIST =
            new TypeReference<>() {};

    private final AiTripGenerationSessionServiceImpl sessionService;
    private final AiTripDayGenerationServiceImpl dayGenerationService;
    private final TripDayGenerateWorkflow tripDayGenerateWorkflow;
    private final TripTimelineAssembler tripTimelineAssembler;
    private final NearbyHotelService nearbyHotelService;
    private final JsonCodec jsonCodec;

    /**
     * 生成指定日期的行程计划。
     *
     * <p>整体流程：幂等检查 → 创建/复用生成记录 → 执行工作流节点 → 持久化结果。 支持强制重新生成（forceRegenerate=true）和异步复用（ASYNC模式）两种策略。
     *
     * @param sessionId 生成会话ID
     * @param dayNo 第几天（从1开始）
     * @param requestMode 请求模式，如 "SYNC"（同步）、"ASYNC"（异步）
     * @param forceRegenerate 是否强制重新生成，忽略已有结果
     * @return 生成记录（包含状态和结果JSON）
     */
    public AiTripDayGeneration generateDay(
            String sessionId, Integer dayNo, String requestMode, boolean forceRegenerate) {
        return generateDay(sessionId, dayNo, requestMode, forceRegenerate, null);
    }

    public AiTripDayGeneration generateDay(
            String sessionId,
            Integer dayNo,
            String requestMode,
            boolean forceRegenerate,
            String revisionText) {
        // 1. 校验会话状态：必须为已准备（PREPARED）状态才能生成行程
        AiTripGenerationSession session = requirePreparedSession(sessionId);
        AiTripDayGeneration latest = dayGenerationService.getLatest(sessionId, dayNo);
        TripPlanDTO.DailyPlan previousTargetPlan =
                forceRegenerate && latest != null && latest.getResultJson() != null
                        ? readGeneratedDay(latest)
                        : null;

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
            DayGenerateInput input = timed("restore-input", () -> restoreInput(session, dayNo));
            input.setPreviousTargetDailyPlan(previousTargetPlan);
            String effectiveRevisionText =
                    effectiveRevisionText(revisionText, forceRegenerate, previousTargetPlan, day);
            DayGenerateResult result =
                    timed(
                            "trip-day-generate-workflow",
                            () ->
                                    executeWorkflowWithRetry(
                                            input, effectiveRevisionText, sessionId, dayNo));
            TripPlanDTO.DailyPlan generatedPlan = result.getDailyPlan();
            // 填充附近酒店数据（所有天数均执行，确保持久化结果包含酒店信息）
            try {
                nearbyHotelService.fillNearbyHotels(List.of(generatedPlan));
            } catch (Exception hotelException) {
                log.warn("填充附近酒店失败，sessionId={} dayNo={}，继续保存", sessionId, dayNo, hotelException);
            }
            // 酒店数据已就绪，重新组装 timeline 使 TRANSFER/DAY_START 节点标题和标签包含真实酒店名+价格
            try {
                List<TripPlanDTO.DailyPlan> prevDays =
                        input.getPreviousDailyPlans() != null
                                ? input.getPreviousDailyPlans()
                                : List.of();
                tripTimelineAssembler.assemble(
                        prevDays,
                        List.of(generatedPlan),
                        new TripTimelineAssembler.TimelineInput(
                                prevDays,
                                List.of(generatedPlan),
                                input.getRequirement(),
                                input.getSelectedQuote(),
                                input.getRentalTripContext(),
                                input.getDaySkeletons() != null
                                        ? input.getDaySkeletons()
                                        : List.of(),
                                List.of()));
            } catch (Exception timelineException) {
                log.warn(
                        "重新组装timeline失败，sessionId={} dayNo={}，使用workflow结果继续",
                        sessionId,
                        dayNo,
                        timelineException);
            }
            // 将酒店数据挂到 STAY_AREA 时间线节点上（供前端地图渲染酒店标记和详情）
            nearbyHotelService.enrichStayAreaNode(generatedPlan);
            // 生成成功，将当前 dayNo 的计划 JSON 写入结果
            timed(
                    "day-mark-generated",
                    () ->
                            dayGenerationService.markGenerated(
                                    day.getId(), jsonCodec.write(generatedPlan, "单日生成数据序列化失败")));
            AiTripDayGeneration generated =
                    timed(
                            "day-load-generated",
                            () -> dayGenerationService.getLatest(sessionId, dayNo));
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

    private String effectiveRevisionText(
            String revisionText,
            boolean forceRegenerate,
            TripPlanDTO.DailyPlan previousTargetPlan,
            AiTripDayGeneration day) {
        String normalizedRevision = normalizeRevisionText(revisionText);
        if (!forceRegenerate || normalizedRevision != null) {
            return normalizedRevision;
        }
        String previousSpotNames = previousSpotNames(previousTargetPlan);
        String rerollRevision =
                "强制重新生成本日行程：不要直接复用上一版景点组合、顺序和时间线；"
                        + "上一版景点="
                        + previousSpotNames
                        + "；请优先替换 1-2 个景点，重新评估游览顺序，并保留午餐、晚餐和晚餐后的夜游节点；"
                        + "reroll="
                        + day.getGenerationVersion()
                        + "-"
                        + System.currentTimeMillis();
        String normalizedReroll = normalizeRevisionText(rerollRevision);
        log.info(
                "强制重新生成加入 reroll 指令，sessionId={} day={} revision={}",
                day.getSessionId(),
                day.getDayNo(),
                normalizedReroll);
        return normalizedReroll;
    }

    private String previousSpotNames(TripPlanDTO.DailyPlan previousTargetPlan) {
        if (previousTargetPlan == null
                || previousTargetPlan.getSpots() == null
                || previousTargetPlan.getSpots().isEmpty()) {
            return "无";
        }
        List<String> names =
                previousTargetPlan.getSpots().stream()
                        .map(TripPlanDTO.Spot::getName)
                        .filter(name -> name != null && !name.isBlank())
                        .distinct()
                        .limit(6)
                        .toList();
        return names.isEmpty() ? "无" : String.join("、", names);
    }

    private DayGenerateResult executeWorkflowWithRetry(
            DayGenerateInput input, String revisionText, String sessionId, Integer dayNo) {
        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= MAX_WORKFLOW_ATTEMPTS; attempt++) {
            input.setRevisionText(retryRevisionText(revisionText, attempt, lastException));
            try {
                if (attempt > 1) {
                    log.warn(
                            "单日行程生成触发后端重试，sessionId={} day={} attempt={} revision={}",
                            sessionId,
                            dayNo,
                            attempt,
                            input.getRevisionText());
                }
                return tripDayGenerateWorkflow.execute(input);
            } catch (RuntimeException exception) {
                lastException = exception;
                if (!shouldRetryWorkflow(exception) || attempt >= MAX_WORKFLOW_ATTEMPTS) {
                    throw exception;
                }
            }
        }
        throw lastException == null
                ? new BusinessException(ErrorCode.AI_GENERATE_ERROR, "单日行程生成失败")
                : lastException;
    }

    private boolean shouldRetryWorkflow(RuntimeException exception) {
        if (!(exception instanceof BusinessException businessException)
                || businessException.getErrorCode() != ErrorCode.AI_GENERATE_ERROR) {
            return false;
        }
        String message = businessException.getMessage() == null ? "" : businessException.getMessage();
        return message.contains("行程数据不完整")
                || message.contains("每天应安排 2-4 个景点")
                || message.contains("景点疑似景区内部设施")
                || message.contains("当天景点点位过近")
                || message.contains("景点缺少经纬度")
                || message.contains("路线段数量与景点数量不匹配")
                || message.contains("当天路线总距离过长")
                || message.contains("当天存在过长单段路线");
    }

    private String retryRevisionText(
            String originalRevision, int attempt, RuntimeException lastException) {
        String base = originalRevision == null || originalRevision.isBlank() ? "" : originalRevision + " ";
        if (attempt <= 1) {
            return originalRevision;
        }
        String reason = lastException == null || lastException.getMessage() == null ? "" : lastException.getMessage();
        String retryHint =
                "后端重试要求：上一版不合格，必须重新选择 2-4 个真实可游览景点；"
                        + "禁止选择管理局、管委会、管理处、游客中心、停车场、售票处、厕所、办公室、监控室等内部设施；"
                        + "同一天景点不要贴在同一景区内部，优先从同城或周边真实热门景点补足。"
                        + (reason.isBlank() ? "" : " 上次失败原因：" + reason);
        return normalizeRevisionText(base + retryHint);
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

    /** 恢复生成工作流输入。 */
    private DayGenerateInput restoreInput(AiTripGenerationSession session, Integer dayNo) {
        return new DayGenerateInput(
                session.getUserId(),
                jsonCodec.read(
                        session.getRequirementJson(), TravelRequirementDTO.class, "生成会话数据解析失败"),
                jsonCodec.readNullable(
                        session.getSelectedQuoteJson(), RentalQuoteOptionDTO.class, "生成会话数据解析失败"),
                jsonCodec.readNullable(
                        session.getRentalTripContextJson(),
                        RentalTripContextDTO.class,
                        "生成会话数据解析失败"),
                jsonCodec.read(session.getDaySkeletonsJson(), DAY_SKELETON_LIST, "生成会话数据解析失败"),
                jsonCodec.read(session.getCityProfileJson(), CityProfile.class, "生成会话数据解析失败"),
                session.getWeatherJson(),
                session.getHotelJson(),
                readGeneratedPreviousDays(session.getSessionId(), dayNo),
                null,
                dayNo,
                null);
    }

    /**
     * 读取已生成的前一天行程数据。
     *
     * @param sessionId
     * @param dayNo
     * @return
     */
    private List<TripPlanDTO.DailyPlan> readGeneratedPreviousDays(String sessionId, Integer dayNo) {
        List<TripPlanDTO.DailyPlan> days =
                dayGenerationService.listCurrentGeneratedBefore(sessionId, dayNo).stream()
                        .map(this::readGeneratedDay)
                        .toList();
        // 为缺少酒店数据的旧记录补充填充（新记录在生成时已包含）
        for (TripPlanDTO.DailyPlan day : days) {
            if (day.getNearbyHotels() == null || day.getNearbyHotels().isEmpty()) {
                try {
                    nearbyHotelService.fillNearbyHotels(List.of(day));
                } catch (Exception e) {
                    log.warn("补充填充前一天酒店失败，day={}", day.getDay(), e);
                }
            }
        }
        return days;
    }

    /**
     * 读取已生成的单日行程数据。
     *
     * @param day
     * @return
     */
    private TripPlanDTO.DailyPlan readGeneratedDay(AiTripDayGeneration day) {
        return jsonCodec.read(day.getResultJson(), TripPlanDTO.DailyPlan.class, "已生成单日行程数据解析失败");
    }

    private void timed(String node, Runnable action) {
        WorkflowTiming.run("generate-day", node, action);
    }

    private <T> T timed(String node, java.util.function.Supplier<T> action) {
        return WorkflowTiming.call("generate-day", node, action);
    }
}
