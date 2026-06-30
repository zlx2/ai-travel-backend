package com.sora.aitravel.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sora.aitravel.common.result.*;
import com.sora.aitravel.common.utils.LoginUserUtils;
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
import com.sora.aitravel.service.AiTripDayGenerateService;
import com.sora.aitravel.service.AiTripDayGenerationService;
import com.sora.aitravel.service.AiTripGenerationOrchestrator;
import com.sora.aitravel.service.AiTripGenerationSessionService;
import com.sora.aitravel.service.TripDayGenerateMessageProducer;
import com.sora.aitravel.workflow.analyze.AnalyzeWorkflowContext;
import com.sora.aitravel.workflow.analyze.TripAnalyzeWorkflow;
import com.sora.aitravel.workflow.generate.GenerateWorkflowContext;
import com.sora.aitravel.workflow.generate.TripTimelineAssembler;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 智能旅行规划控制器（AI 分析+生成行程）。
 *
 * <p>接口前缀：/api/ai/trips
 *
 * <p>请求方式：POST
 *
 * <p>权限要求：所有接口均需登录（@SaCheckLogin）
 */
@SaCheckLogin
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai/trips")
public class AiTripController {

    private final TripAnalyzeWorkflow tripAnalyzeWorkflow;
    private final AiTripGenerationOrchestrator aiTripGenerationOrchestrator;
    private final AiTripDayGenerateService aiTripDayGenerateService;
    private final AiTripDayGenerationService aiTripDayGenerationService;
    private final AiTripGenerationSessionService aiTripGenerationSessionService;
    private final TripDayGenerateMessageProducer tripDayGenerateMessageProducer;
    private final TripTimelineAssembler tripTimelineAssembler;
    private final ObjectMapper objectMapper;

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
                    objectMapper.readValue(
                            session.getRequirementJson(), TravelRequirementDTO.class);
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
            tripDayGenerateMessageProducer.send(
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
                                20,
                                null,
                                null);
                        TripGenerateResponse result =
                                generateFirstDayAndPrefetchNext(userId, request);
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
                    objectMapper.readValue(
                            session.getRequirementJson(), TravelRequirementDTO.class);
            TripPlanDTO.DailyPlan dailyPlan =
                    objectMapper.readValue(day.getResultJson(), TripPlanDTO.DailyPlan.class);
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
                    objectMapper.readValue(session.getRequirementJson(), TravelRequirementDTO.class);
            TripPlanDTO.DailyPlan dailyPlan =
                    objectMapper.readValue(day.getResultJson(), TripPlanDTO.DailyPlan.class);
            assembleTimelineIfMissing(
                    session,
                    requirement,
                    dailyPlan,
                    readNullable(session.getSelectedQuoteJson(), RentalQuoteOptionDTO.class));
            return objectMapper.writeValueAsString(dailyPlan);
        } catch (Exception exception) {
            log.warn("单日旧结果补 timeline 失败，sessionId={}, dayNo={}", sessionId, day.getDayNo(), exception);
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
        GenerateWorkflowContext context =
                GenerateWorkflowContext.builder()
                        .userId(session.getUserId())
                        .requirement(requirement)
                        .selectedQuote(selectedQuote)
                        .rentalTripContext(
                                readNullable(
                                        session.getRentalTripContextJson(), RentalTripContextDTO.class))
                        .lockedDailyPlans(List.of(dailyPlan))
                        .singleDayGeneration(true)
                        .build();
        tripTimelineAssembler.assemble(List.of(), context.getLockedDailyPlans(), context);
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

    private <T> T readNullable(String json, Class<T> type) {
        if (json == null || json.isBlank() || "null".equals(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception exception) {
            throw new IllegalStateException("生成会话上下文解析失败", exception);
        }
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

    private String progressLabel(String node) {
        return Map.ofEntries(
                        Map.entry("requirement-validate", "正在校验旅行需求"),
                        Map.entry("requirement-load", "正在读取目的地和人数信息"),
                        Map.entry("city-data-profile", "正在整理城市景点资料"),
                        Map.entry("candidate-pool-build", "正在整理路线候选区域"),
                        Map.entry("ai-macro-route-plan", "正在规划多日路线方向"),
                        Map.entry("amap-macro-route-fact", "正在核算路线距离和耗时"),
                        Map.entry("ai-route-critic", "正在检查路线是否顺路"),
                        Map.entry("macro-route-contract-validate", "正在锁定每日出发和住宿区域"),
                        Map.entry("weather-fetch", "正在查询目的地天气"),
                        Map.entry("hotel-fetch", "正在准备住宿参考"),
                        Map.entry("day-state-init", "正在初始化每日行程状态"),
                        Map.entry("day-context-build", "正在拆分每日游览区域"),
                        Map.entry("day-query-plan", "正在规划景点查询关键词"),
                        Map.entry("food-recommend", "正在匹配餐饮建议"),
                        Map.entry("day-data-fetch", "正在查询景点与路线数据"),
                        Map.entry("day-data-rank", "正在筛选更顺路的景点"),
                        Map.entry("day-plan-generate", "正在生成每日行程和推荐理由"),
                        Map.entry("day-plan-validate", "正在校验路线强度"),
                        Map.entry("trip-summary", "正在整理行程摘要"),
                        Map.entry("result-merge", "正在合并最终行程"))
                .getOrDefault(node, "正在生成行程");
    }
}
