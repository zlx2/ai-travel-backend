package com.sora.aitravel.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.sora.aitravel.common.result.*;
import com.sora.aitravel.common.utils.JsonCodec;
import com.sora.aitravel.common.utils.LoginUserUtils;
import com.sora.aitravel.config.RabbitMqConfig;
import com.sora.aitravel.dto.message.TripDayGenerateMessage;
import com.sora.aitravel.dto.model.RecommendationContextDTO;
import com.sora.aitravel.dto.model.RentalQuoteOptionDTO;
import com.sora.aitravel.dto.model.RentalTripContextDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.model.TripPlanDTO;
import com.sora.aitravel.dto.request.*;
import com.sora.aitravel.dto.response.*;
import com.sora.aitravel.entity.AiTripDayGeneration;
import com.sora.aitravel.entity.AiTripGenerationSession;
import com.sora.aitravel.service.impl.AiTripDayGenerationServiceImpl;
import com.sora.aitravel.service.impl.AiTripGenerationOrchestratorImpl;
import com.sora.aitravel.service.impl.AiTripGenerationSessionServiceImpl;
import com.sora.aitravel.workflow.analyze.AnalyzeWorkflowContext;
import com.sora.aitravel.workflow.analyze.TripAnalyzeWorkflow;
import com.sora.aitravel.workflow.generate.AiTripDayGenerateOrchestrator;
import com.sora.aitravel.workflow.generate.TripTimelineAssembler;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 智能旅行规划控制器（AI 分析+生成行程）。
 *
 * <p>接口前缀：/ai/trips，全局 /api 前缀由 server.servlet.context-path 配置。
 *
 * <p>请求方式：POST
 *
 * <p>权限要求：所有接口均需登录（@SaCheckLogin）
 */
@SaCheckLogin
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/trips")
public class AiTripController {

    private final TripAnalyzeWorkflow tripAnalyzeWorkflow;
    private final AiTripGenerationOrchestratorImpl aiTripGenerationOrchestrator;
    private final AiTripDayGenerateOrchestrator aiTripDayGenerateService;
    private final AiTripDayGenerationServiceImpl aiTripDayGenerationService;
    private final AiTripGenerationSessionServiceImpl aiTripGenerationSessionService;
    private final RabbitTemplate rabbitTemplate;
    private final TripTimelineAssembler tripTimelineAssembler;
    private final JsonCodec jsonCodec;

    /**
     * AI 分析用户旅行需求（需登录）。
     *
     * <p>支持两种模式：用户主动输入模糊需求并提取结构化信息，或对已推荐的目的地确认后返回分析结果。
     *
     * @param request 包含对话 ID、用户输入、扩展答案和已选目的地的分析请求
     * @return 分析结果，包含结构化需求、追问问题、目的地推荐和冲突检测（TripAnalyzeResponse）
     */
    @PostMapping("/analyze")
    public R<TripAnalyzeResponse> analyze(@Valid @RequestBody TripAnalyzeRequest request) {
        AnalyzeWorkflowContext context = new AnalyzeWorkflowContext();
        context.setUserId(LoginUserUtils.getUserId());
        context.setRequest(request);
        return R.ok(tripAnalyzeWorkflow.execute(context).getResult());
    }

    /**
     * AI 根据用户需求生成完整旅行计划（需登录）。
     *
     * @param request 包含对话 ID、确认的冲突标记、结构化需求和冲突列表的生成请求
     * @return 生成的详细行程计划，包含每日安排和预算（TripGenerateResponse）
     */
    @PostMapping("/generate")
    public R<TripGenerateResponse> generate(@Valid @RequestBody TripGenerateRequest request) {
        return R.ok(generateFirstDayAndPrefetchNext(LoginUserUtils.getUserId(), request));
    }

    @PostMapping("/generate/session")
    public R<TripGenerateSessionResponse> prepareSession(
            @Valid @RequestBody TripGenerateRequest request) {
        AiTripGenerationSession session =
                aiTripGenerationOrchestrator.prepareSession(LoginUserUtils.getUserId(), request);
        return R.ok(
                new TripGenerateSessionResponse(
                        session.getSessionId(),
                        session.getConversationId(),
                        session.getStatus(),
                        session.getErrorMessage()));
    }

    @PostMapping("/generate/session/{sessionId}/days/{dayNo}")
    public R<TripGenerateDayResponse> generateDay(
            @PathVariable String sessionId,
            @PathVariable Integer dayNo,
            @RequestParam(defaultValue = "USER") String requestMode,
            @RequestParam(defaultValue = "false") boolean forceRegenerate,
            @RequestParam(defaultValue = "false") boolean prefetchNext,
            @RequestParam(required = false) String revisionText) {
        AiTripDayGeneration day =
                aiTripDayGenerateService.generateDay(
                        sessionId, dayNo, requestMode, forceRegenerate, revisionText);
        if (prefetchNext) {
            enqueueNextDay(sessionId, dayNo);
        }
        return R.ok(
                new TripGenerateDayResponse(
                        day.getSessionId(),
                        day.getDayNo(),
                        day.getGenerationVersion(),
                        day.getIsCurrent(),
                        day.getStatus(),
                        normalizeDayResultJson(sessionId, day),
                        day.getErrorMessage()));
    }

    private void enqueueNextDay(String sessionId, Integer dayNo) {
        try {
            AiTripGenerationSession session =
                    aiTripGenerationSessionService.getBySessionId(sessionId);
            if (session == null || session.getRequirementJson() == null) {
                return;
            }
            TravelRequirementDTO requirement =
                    jsonCodec.read(
                            session.getRequirementJson(),
                            TravelRequirementDTO.class,
                            "预取下一日行程时需求数据解析失败");
            int nextDay = dayNo + 1;
            if (requirement.getDays() == null || nextDay > requirement.getDays()) {
                return;
            }
            AiTripDayGeneration queuedDay =
                    aiTripDayGenerationService.createQueuedIfAbsent(
                            sessionId, session.getUserId(), nextDay, "ASYNC");
            if (!"QUEUED".equals(queuedDay.getStatus())) {
                return;
            }
            publishTripDayGenerateMessage(
                    new TripDayGenerateMessage(
                            sessionId,
                            session.getUserId(),
                            nextDay,
                            "ASYNC",
                            false,
                            UUID.randomUUID().toString()));
        } catch (Exception exception) {
            log.warn("投递下一天预生成失败，sessionId={}, dayNo={}", sessionId, dayNo, exception);
        }
    }

    @PostMapping(value = "/generate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generateStream(@Valid @RequestBody TripGenerateRequest request) {
        SseEmitter emitter = new SseEmitter(300_000L);
        Long userId = LoginUserUtils.getUserId();
        CompletableFuture.runAsync(
                () -> {
                    try {
                        sendProgress(emitter, "start", "start", "开始生成行程", 1, null, null);
                        sendProgress(
                                emitter,
                                "progress",
                                "prepare-session",
                                "正在准备行程上下文",
                                15,
                                null,
                                null);

                        AiTripGenerationSession session =
                                aiTripGenerationOrchestrator.prepareSession(userId, request);

                        sendProgress(
                                emitter,
                                "progress",
                                "generating-day1",
                                "正在调用AI生成第1天行程",
                                30,
                                null,
                                null);

                        AiTripDayGeneration day =
                                aiTripDayGenerateService.generateDay(
                                        session.getSessionId(), 1, "USER", false);

                        sendProgress(
                                emitter,
                                "progress",
                                "parsing-result",
                                "正在解析AI生成的行程数据",
                                50,
                                null,
                                null);

                        TravelRequirementDTO requirement =
                                jsonCodec.read(
                                        session.getRequirementJson(),
                                        TravelRequirementDTO.class,
                                        "组装生成响应时需求数据解析失败");
                        TripPlanDTO.DailyPlan dailyPlan =
                                jsonCodec.read(
                                        day.getResultJson(),
                                        TripPlanDTO.DailyPlan.class,
                                        "组装生成响应时单日行程数据解析失败");

                        sendProgress(
                                emitter,
                                "progress",
                                "assembling-timeline",
                                "正在规划每日时间线",
                                65,
                                null,
                                null);

                        assembleTimelineIfMissing(
                                session, requirement, dailyPlan, request.getSelectedQuote());

                        sendProgress(
                                emitter,
                                "progress",
                                "calculating-budget",
                                "正在计算行程预算",
                                75,
                                null,
                                null);

                        TripPlanDTO tripPlan =
                                new TripPlanDTO(
                                        displayDestination(requirement)
                                                + requirement.getDays()
                                                + "日旅行方案",
                                        displayDestination(requirement),
                                        requirement.getDays(),
                                        "已生成第 " + dailyPlan.getDay() + " 天行程，后续天数将按需生成。",
                                        null,
                                        List.of(dailyPlan),
                                        budgetSummary(List.of(dailyPlan)),
                                        List.of("下一天行程会在后台预生成，点击对应日期时优先返回已生成版本。"),
                                        new TripPlanDTO.DataQuality(
                                                "AMAP",
                                                "AMAP",
                                                "AMAP_AVERAGE_COST_AND_ROUTE;TICKET_HOTEL_UNAVAILABLE"));

                        sendProgress(
                                emitter,
                                "progress",
                                "prefetching-next",
                                "正在调度后续天数预生成",
                                85,
                                null,
                                null);

                        enqueueNextDay(session.getSessionId(), 1);

                        sendProgress(emitter, "progress", "finalizing", "正在封装行程结果", 95, null, null);

                        TripGenerateResponse result =
                                new TripGenerateResponse(
                                        "trip-plan-v1",
                                        session.getConversationId(),
                                        session.getSessionId(),
                                        requirement,
                                        request.getSelectedQuote(),
                                        (RecommendationContextDTO) null,
                                        tripPlan,
                                        buildDayStatuses(requirement.getDays(), day));

                        sendProgress(emitter, "done", "done", "第 1 天行程生成完成", 100, result, null);
                        emitter.complete();
                    } catch (Exception ex) {
                        log.warn("AI 行程流式生成失败", ex);
                        sendProgress(
                                emitter, "error", "error", "行程生成失败", null, null, "行程生成失败，请稍后重试");
                        emitter.complete();
                    }
                });
        return emitter;
    }

    private TripGenerateResponse generateFirstDayAndPrefetchNext(
            Long userId, TripGenerateRequest request) {
        AiTripGenerationSession session =
                aiTripGenerationOrchestrator.prepareSession(userId, request);
        AiTripDayGeneration day =
                aiTripDayGenerateService.generateDay(session.getSessionId(), 1, "USER", false);

        enqueueNextDay(session.getSessionId(), 1);

        return buildGenerateResponse(session, day, request.getSelectedQuote());
    }

    private TripGenerateResponse buildGenerateResponse(
            AiTripGenerationSession session,
            AiTripDayGeneration day,
            RentalQuoteOptionDTO selectedQuote) {
        try {
            TravelRequirementDTO requirement =
                    jsonCodec.read(
                            session.getRequirementJson(),
                            TravelRequirementDTO.class,
                            "组装生成响应时需求数据解析失败");
            TripPlanDTO.DailyPlan dailyPlan =
                    jsonCodec.read(
                            day.getResultJson(), TripPlanDTO.DailyPlan.class, "组装生成响应时单日行程数据解析失败");
            assembleTimelineIfMissing(session, requirement, dailyPlan, selectedQuote);
            TripPlanDTO tripPlan =
                    new TripPlanDTO(
                            displayDestination(requirement) + requirement.getDays() + "日旅行方案",
                            displayDestination(requirement),
                            requirement.getDays(),
                            "已生成第 " + dailyPlan.getDay() + " 天行程，后续天数将按需生成。",
                            null,
                            List.of(dailyPlan),
                            budgetSummary(List.of(dailyPlan)),
                            List.of("下一天行程会在后台预生成，点击对应日期时优先返回已生成版本。"),
                            new TripPlanDTO.DataQuality(
                                    "AMAP",
                                    "AMAP",
                                    "AMAP_AVERAGE_COST_AND_ROUTE;TICKET_HOTEL_UNAVAILABLE"));
            return new TripGenerateResponse(
                    "trip-plan-v1",
                    session.getConversationId(),
                    session.getSessionId(),
                    requirement,
                    selectedQuote,
                    (RecommendationContextDTO) null,
                    tripPlan,
                    buildDayStatuses(requirement.getDays(), day));
        } catch (Exception exception) {
            throw new IllegalStateException("组装单日生成响应失败", exception);
        }
    }

    private String normalizeDayResultJson(String sessionId, AiTripDayGeneration day) {
        if (day == null || day.getResultJson() == null || !"GENERATED".equals(day.getStatus())) {
            return day == null ? null : day.getResultJson();
        }
        try {
            AiTripGenerationSession session =
                    aiTripGenerationSessionService.getBySessionId(sessionId);
            if (session == null) {
                return day.getResultJson();
            }
            TravelRequirementDTO requirement =
                    jsonCodec.read(
                            session.getRequirementJson(),
                            TravelRequirementDTO.class,
                            "单日旧结果补 timeline 时需求数据解析失败");
            TripPlanDTO.DailyPlan dailyPlan =
                    jsonCodec.read(
                            day.getResultJson(),
                            TripPlanDTO.DailyPlan.class,
                            "单日旧结果补 timeline 时单日行程数据解析失败");
            assembleTimelineIfMissing(
                    session,
                    requirement,
                    dailyPlan,
                    jsonCodec.readNullable(
                            session.getSelectedQuoteJson(),
                            RentalQuoteOptionDTO.class,
                            "生成会话上下文解析失败"));
            return jsonCodec.write(dailyPlan, "单日旧结果补 timeline 失败");
        } catch (Exception exception) {
            log.warn(
                    "单日旧结果补 timeline 失败，sessionId={}, dayNo={}",
                    sessionId,
                    day.getDayNo(),
                    exception);
            return day.getResultJson();
        }
    }

    private void assembleTimelineIfMissing(
            AiTripGenerationSession session,
            TravelRequirementDTO requirement,
            TripPlanDTO.DailyPlan dailyPlan,
            RentalQuoteOptionDTO selectedQuote) {
        if (dailyPlan.getTimeline() != null && !dailyPlan.getTimeline().isEmpty()) {
            return;
        }
        RentalTripContextDTO rentalTripContext =
                jsonCodec.readNullable(
                        session.getRentalTripContextJson(),
                        RentalTripContextDTO.class,
                        "生成会话上下文解析失败");
        tripTimelineAssembler.assemble(
                List.<TripPlanDTO.DailyPlan>of(),
                List.of(dailyPlan),
                new TripTimelineAssembler.TimelineInput(
                        List.of(),
                        List.of(dailyPlan),
                        requirement,
                        selectedQuote,
                        rentalTripContext,
                        List.of(),
                        List.of()));
    }

    private List<TripGenerateDayStatusResponse> buildDayStatuses(
            Integer days, AiTripDayGeneration generatedDay) {
        if (days == null || days <= 0) {
            return List.of();
        }
        return java.util.stream.IntStream.rangeClosed(1, days)
                .mapToObj(
                        dayNo -> {
                            if (generatedDay.getDayNo().equals(dayNo)) {
                                return new TripGenerateDayStatusResponse(
                                        generatedDay.getDayNo(),
                                        generatedDay.getGenerationVersion(),
                                        generatedDay.getIsCurrent(),
                                        generatedDay.getStatus(),
                                        generatedDay.getErrorMessage());
                            }
                            return new TripGenerateDayStatusResponse(
                                    dayNo, null, 0, "NOT_STARTED", null);
                        })
                .toList();
    }

    private TripPlanDTO.BudgetSummary budgetSummary(List<TripPlanDTO.DailyPlan> dailyPlans) {
        int tickets = 0;
        int food = 0;
        int transport = 0;
        for (TripPlanDTO.DailyPlan day : dailyPlans) {
            if (day.getEstimatedCost() == null) {
                continue;
            }
            tickets += value(day.getEstimatedCost().getTickets());
            food += value(day.getEstimatedCost().getFood());
            transport += value(day.getEstimatedCost().getTransport());
        }
        TripPlanDTO.BudgetSummary summary = new TripPlanDTO.BudgetSummary();
        summary.setTicketCost(tickets);
        summary.setFoodCost(food);
        summary.setTransportCost(transport);
        summary.setHotelCost(null);
        summary.setTotalEstimatedCost(tickets + food + transport);
        summary.setTicketSource("UNAVAILABLE");
        summary.setHotelSource("UNAVAILABLE");
        summary.setExcludesUnknownItems(true);
        return summary;
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private String displayDestination(TravelRequirementDTO requirement) {
        if (requirement.getDestination() != null && !requirement.getDestination().isBlank()) {
            return requirement.getDestination();
        }
        if (requirement.getRouteRegion() != null && !requirement.getRouteRegion().isBlank()) {
            return requirement.getRouteRegion();
        }
        return requirement.getRouteCities() == null
                ? ""
                : String.join("-", requirement.getRouteCities());
    }

    private boolean sendProgress(
            SseEmitter emitter,
            String type,
            String node,
            String label,
            Integer progress,
            TripGenerateResponse data,
            String message) {
        try {
            emitter.send(
                    SseEmitter.event()
                            .name(type)
                            .data(
                                    new TripGenerateProgressEvent(
                                            type, node, label, progress, data, message)));
            return true;
        } catch (IOException ex) {
            log.warn("SSE progress send failed, type={}, node={}", type, node, ex);
            return false;
        }
    }

    private void publishTripDayGenerateMessage(TripDayGenerateMessage message) {
        rabbitTemplate.convertAndSend(
                RabbitMqConfig.TRIP_DAY_GENERATE_EXCHANGE,
                RabbitMqConfig.TRIP_DAY_GENERATE_ROUTING_KEY,
                message);
        log.info(
                "已投递单日行程生成消息，sessionId={}, dayNo={}, mode={}",
                message.sessionId(),
                message.dayNo(),
                message.requestMode());
    }
}
