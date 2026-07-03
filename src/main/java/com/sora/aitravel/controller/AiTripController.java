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
import com.sora.aitravel.service.impl.NearbyHotelService;
import com.sora.aitravel.workflow.analyze.AnalyzeWorkflowContext;
import com.sora.aitravel.workflow.analyze.TripAnalyzeWorkflow;
import com.sora.aitravel.workflow.generate.AiTripDayGenerateOrchestrator;
import com.sora.aitravel.workflow.generate.TripTimelineAssembler;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.ArrayList;
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
 * AI 智能旅行规划控制器。
 *
 * 负责处理所有与 AI 行程生成相关的 HTTP 请求入口：
 * - 需求分析：将用户自然语言模糊需求提取为结构化 TravelRequirementDTO
 * - 行程准备：准备城市资料、景点候选、宏观路线骨架、天气数据
 * - 按天生成：基于骨架逐步生成每日行程，支持异步预取后续天数
 * - 流式生成：通过 SSE 推送实时生成进度给前端
 *
 * 整体工作流调度逻辑：
 * 1. 第一步：用户输入自然语言 → /analyze → TripAnalyzeWorkflow → 提取结构化需求
 * 2. 第二步：用户确认需求 → /generate → TripPrepareWorkflow 准备骨架
 *                  → TripDayGenerateWorkflow 生成第一天 → 异步投递 RabbitMQ 生成第二天
 * 3. 第三步：用户点击后续天数 → /generate/session/{id}/days/{n} → 生成当天
 *                  → 异步投递下一天，以此类推直到全部完成
 * 4. 流式版本：/generate/stream → SSE 逐段推送进度，用户体验更好
 *
 * 接口前缀：/ai/trips，全局 /api 前缀由 server.servlet.context-path 配置。
 * 请求方式：主要接口均为 POST。
 * 权限要求：所有接口均需登录（@SaCheckLogin 注解全局生效）。
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
    private final NearbyHotelService nearbyHotelService;

    /**
     * AI 分析用户旅行需求（需登录）。
     *
     * 调用 TripAnalyzeWorkflow（7 节点）对用户输入进行需求分析。
     * 支持多轮对话：对于无法一次性提取完整需求的模糊输入，会返回追问问题，
     * 前端收集用户补充回答后再次调用本接口，直到需求完整（status=READY）。
     *
     * 工作流内部节点顺序：
     * 1. user-raw-input：接收并校验原始请求
     * 2. input-preprocess：将用户输入、表单、追问回答、已选目的地合并为清洁文本
     * 3. info-extract：规则优先从文本中抽取结构化需求，规则失败时 AI 兜底
     * 4. requirement-standardize：按优先级（表单 > 确认 > AI 抽取）合并标准化各字段
     * 5. completeness-check：检查目的地和天数是否齐全，缺失则生成追问
     * 6. conflict-check：对信息齐全的需求进行六项规则冲突检测
     * 7. result-merge：组装最终响应，包含状态、需求、追问、冲突等
     *
     * 返回的 TripAnalyzeResponse.status 有三种可能：
     * - NEED_MORE_INFO：信息不完整，需要用户补充回答
     * - READY：需求完整，可以进入行程生成阶段
     * - CONFLICT：需求存在冲突（如预算过低、天数不足等），需要用户调整
     *
     * 分析阶段不持久化数据，结果由前端暂存，确认后通过 /generate 接口传入。
     *
     * @param request 分析请求，包含：
     *                - conversationId：对话 ID，首次为空，后续轮次携带上次返回的 conversationId
     *                - userInput：用户自由文本输入，如"想去大理玩5天，轻松一点"
     *                - formInput：前端表单填写的结构化需求字段（TravelRequirementDTO）
     *                - requirement：上一轮确认的需求（用于多轮对话合并）
     *                - extraAnswers：用户对追问问题的补充回答列表
     *                - selectedDestination：用户在前端已选中的目的地
     * @return TripAnalyzeResponse 分析结果，包含：
     *         - conversationId：对话 ID，多轮对话时需携带
     *         - status：当前状态（NEED_MORE_INFO / READY / CONFLICT）
     *         - requirement：抽取出的结构化需求 TravelRequirementDTO
     *         - questions：需要追问的问题列表 QuestionDTO（仅 NEED_MORE_INFO 时有值）
     *         - conflicts：冲突列表 ConflictDTO（仅 CONFLICT 时有值）
     *         - destinationSuggestions：目的地推荐列表（预留）
     *         - askRound：当前追问轮次
     */
    @PostMapping("/analyze")
    public R<TripAnalyzeResponse> analyze(@Valid @RequestBody TripAnalyzeRequest request) {
        AnalyzeWorkflowContext context = new AnalyzeWorkflowContext();
        context.setUserId(LoginUserUtils.getUserId());
        context.setRequest(request);
        return R.ok(tripAnalyzeWorkflow.execute(context).getResult());
    }

    /**
     * AI 完整生成旅行计划（需登录）。
     *
     * 这是一个复合接口，内部串联执行三个步骤：
     * 1. 调用 TripPrepareWorkflow 准备行程骨架（4 节点）：
     *    - destination-prepare：查询目的地景点、餐饮、片区 POI 数据
     *    - macro-route-prepare：贪心分配天数，逐天选择焦点片区，生成主题
     *    - external-context-prepare：查询 Open-Meteo 天气数据
     *    - prepare-finalize：校验骨架，初始化逐日生成状态
     * 2. 调用 TripDayGenerateWorkflow 生成第 1 天行程（4 节点）：
     *    - day-input-prepare：构建 DayContext 和 DayQueryPlan
     *    - day-candidate-prepare：高德 POI 搜索 + 排序，生成 DayDataPackage
     *    - day-plan-generate：规则筛选候选 + AI 选点 + 推荐理由 + 费用估算
     *    - day-plan-finalize：组装 Timeline 时间线 + 分级校验
     * 3. 异步投递 RabbitMQ 消息预取第 2 天行程
     *
     * 生成的完整数据通过 ai_trip_generation_session 表（会话级）和
     * ai_trip_day_generation 表（单日级）持久化。
     *
     * 前端可通过 /generate/session/{sessionId}/days/{n} 逐天获取后续天数。
     *
     * @param request 生成请求，包含：
     *                - conversationId：关联 /analyze 阶段的对话 ID
     *                - requirement：经过确认的完整结构化需求 TravelRequirementDTO
     *                - selectedQuote：用户选中的租车报价方案（非租车场景为 null）
     *                - rentalTripContext：租车上下文（取车/还车城市、时间等）
     * @return TripGenerateResponse 生成结果，包含：
     *         - planVersion：方案版本号（"trip-plan-v1"）
     *         - conversationId：对话 ID
     *         - sessionId：生成会话 ID，用于后续逐天获取
     *         - requirement：最终确认的需求
     *         - selectedQuote：选中的租车报价
     *         - tripPlan：行程计划 TripPlanDTO（含第 1 天 DailyPlan、预算摘要、数据质量说明）
     *         - dayStatuses：各天生成状态列表
     */
    @PostMapping("/generate")
    public R<TripGenerateResponse> generate(@Valid @RequestBody TripGenerateRequest request) {
        return R.ok(generateFirstDayAndPrefetchNext(LoginUserUtils.getUserId(), request));
    }

    /**
     * 仅执行行程准备阶段，不生成任何天（需登录）。
     *
     * 与 /generate 的区别：/generate 会准备 + 生成第 1 天 + 异步预取第 2 天，
     * 而本接口仅执行 TripPrepareWorkflow，返回会话 ID 供后续逐天生成。
     *
     * 适用场景：
     * - 前端需要先展示骨架预览，让用户确认后再开始生成
     * - 需要将准备阶段和生成阶段解耦为两次独立的请求
     *
     * 执行流程：
     * 1. 校验需求有效性
     * 2. 创建 ai_trip_generation_session 记录（status=PREPARING）
     * 3. 执行 TripPrepareWorkflow（4 节点）
     * 4. 将结果（骨架、城市资料、天气、酒店）JSON 序列化持久化到会话表
     * 5. 更新会话状态为 PREPARED
     * 6. 失败 → 状态标记为 FAILED
     *
     * @param request 生成请求，与 /generate 格式相同
     * @return TripGenerateSessionResponse 会话信息，包含：
     *         - sessionId：会话 ID，用于后续 /generate/session/{id}/days/{n} 调用
     *         - conversationId：对话 ID
     *         - status：会话状态（PREPARING / PREPARED / FAILED）
     *         - errorMessage：失败时的错误信息
     */
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

    /**
     * 生成指定会话的指定天行程（需登录）。
     *
     * 调用 AiTripDayGenerateOrchestrator 执行 TripDayGenerateWorkflow（4 节点）。
     * 支持幂等保护：如果该天已生成完成，直接返回已有结果，不重复调用 AI。
     * 支持强制重新生成：forceRegenerate=true 时，旧记录标记为废弃，版本递增。
     *
     * 执行流程：
     * 1. 校验会话状态必须为 PREPARED
     * 2. 幂等检查：已生成 → 直接返回
     * 3. 从 ai_trip_generation_session 表恢复 DayGenerateInput（JSON 反序列化）
     * 4. 读取已生成的前几天行程（用于跨天去重和上下文）
     * 5. 执行 TripDayGenerateWorkflow 生成当天行程
     * 6. 持久化结果到 ai_trip_day_generation 表（status=GENERATED）
     * 7. 如果 prefetchNext=true，自动投递 RabbitMQ 消息预取下一天
     *
     * @param sessionId 生成会话 ID（由 /generate 或 /generate/session 返回）
     * @param dayNo 第几天（从 1 开始，不超过总天数）
     * @param requestMode 请求模式，默认 "USER"：
     *                    - "USER"：用户主动请求
     *                    - "ASYNC"：RabbitMQ 异步消费者触发
     * @param forceRegenerate 是否强制重新生成，默认 false：
     *                         - true：忽略已有结果，旧记录废弃，版本号 +1
     *                         - false：已有结果直接返回（幂等）
     * @param prefetchNext 是否自动预取下一天，默认 false：
     *                     - true：生成完成后投递 RabbitMQ 消息预取 dayNo+1
     * @param revisionText 用户对当前天的修订说明（可选），如"换一个景点"
     * @return TripGenerateDayResponse 单日生成结果，包含：
     *         - sessionId：会话 ID
     *         - dayNo：第几天
     *         - generationVersion：生成版本号
     *         - isCurrent：是否为当前版本（1=是，0=已废弃）
     *         - status：生成状态（QUEUED / GENERATING / GENERATED / FAILED）
     *         - resultJson：序列化的 DailyPlan（含 Timeline）
     *         - errorMessage：失败时的错误信息
     */
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

    /**
     * 异步预取下一日行程生成任务。
     *
     * 在生成当前天（dayNo）后调用，将 dayNo+1 的生成任务投递到 RabbitMQ，
     * 由后台消费者异步执行，实现"用户生成第 1 天时后台自动生成第 2 天"的效果。
     *
     * 执行流程：
     * 1. 从数据库加载会话，获取需求中的总天数
     * 2. 计算下一天（nextDay = dayNo + 1）
     * 3. 如果 nextDay 超过总天数 → 不投递（已是最后一天）
     * 4. 在 ai_trip_day_generation 表中创建 QUEUED 状态记录（幂等：如果已存在则复用）
     * 5. 如果记录状态不是 QUEUED（可能是已生成或生成中）→ 不投递
     * 6. 投递 RabbitMQ 消息到 trip.day.generate.exchange
     * 7. RabbitMQ 消费者 consumeTripDayGenerate() 收到消息后调用 generateDay(ASYNC)
     *
     * 消息格式：TripDayGenerateMessage { sessionId, userId, dayNo, requestMode="ASYNC",
     *           forceRegenerate=false, requestId=UUID }
     *
     * 异常处理：投递失败不阻塞当前请求，仅记录警告日志。
     *
     * @param sessionId 生成会话 ID
     * @param dayNo 当前已生成的天数（将预取 dayNo+1）
     */
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

    /**
     * 流式生成完整旅行计划（需登录，SSE 推送）。
     *
     * 与 /generate 功能相同，但通过 Server-Sent Events 实时推送生成进度，
     * 前端可展示进度条和阶段性状态文字，用户体验更好。
     *
     * SSE 超时时间：300 秒（5 分钟），确保长任务不会意外断开。
     * 执行在 CompletableFuture 异步线程中，不阻塞 Tomcat 工作线程。
     *
     * SSE 事件流（按顺序推送）：
     * 1. event: start      - 进度 0%，"开始生成行程"
     * 2. event: progress   - 进度 15%，"正在准备行程上下文"
     *    此时执行 TripPrepareWorkflow（4 节点），完成骨架、POI 数据、天气查询
     * 3. event: progress   - 进度 30%，"正在调用AI生成第1天行程"
     *    此时执行 TripDayGenerateWorkflow（4 节点），AI 选点生成第 1 天
     * 4. event: progress   - 进度 50%，"正在解析AI生成的行程数据"
     *    从数据库反序列化需求 JSON 和 DailyPlan JSON
     * 5. event: progress   - 进度 65%，"正在规划每日时间线"
     *    调用 TripTimelineAssembler 组装时间线节点序列
     * 6. event: progress   - 进度 75%，"正在计算行程预算"
     *    汇总门票、餐饮、交通费用
     * 7. event: progress   - 进度 85%，"正在调度后续天数预生成"
     *    投递 RabbitMQ 消息预取第 2 天
     * 8. event: progress   - 进度 95%，"正在封装行程结果"
     *    组装 TripGenerateResponse
     * 9. event: done       - 进度 100%，"第 1 天行程生成完成"
     *    携带完整 TripGenerateResponse 数据
     *
     * 异常时推送：
     * event: error - "行程生成失败，请稍后重试"
     *
     * SSE 事件数据结构：TripGenerateProgressEvent {
     *     type, node, label, progress, data, message
     * }
     *
     * @param request 生成请求，与 /generate 格式相同
     * @return SseEmitter SSE 连接对象，Spring MVC 自动管理长连接生命周期
     */
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

                        assembleTimeline(
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
    /**
     * 生成第一日行程并异步预取下一日。
     *
     * 这是 /generate 和 /generate/stream 的公共逻辑，串联以下步骤：
     * 1. prepareSession(userId, request)：执行 TripPrepareWorkflow，持久化会话
     * 2. generateDay(sessionId, dayNo=1, "USER", false)：执行 TripDayGenerateWorkflow，生成第 1 天
     * 3. enqueueNextDay(sessionId, 1)：投递 RabbitMQ 消息预取第 2 天
     * 4. buildGenerateResponse()：组装最终响应
     *
     * @param userId 当前登录用户 ID
     * @param request 生成请求
     * @return TripGenerateResponse 包含会话 ID、第 1 天行程和所有天的状态
     */
    private TripGenerateResponse generateFirstDayAndPrefetchNext(
            Long userId, TripGenerateRequest request) {
        AiTripGenerationSession session =
                aiTripGenerationOrchestrator.prepareSession(userId, request);
        AiTripDayGeneration day =
                aiTripDayGenerateService.generateDay(session.getSessionId(), 1, "USER", false);

        enqueueNextDay(session.getSessionId(), 1);

        return buildGenerateResponse(session, day, request.getSelectedQuote());
    }

    /**
     * 构建生成阶段的完整响应。
     *
     * 从数据库加载需求 JSON 和 DailyPlan JSON，反序列化后组装 TripGenerateResponse。
     * 会自动调用 Timeline 装配和酒店填充，确保响应数据完整。
     *
     * 组装内容：
     * - 从会话表反序列化 TravelRequirementDTO
     * - 从单日生成表反序列化 DailyPlan
     * - 调用 TripTimelineAssembler 为 DailyPlan 装配时间线节点
     * - 构建 TripPlanDTO（标题、目的地、天数、预算摘要、数据质量说明）
     * - 构建各天生成状态列表（第 1 天为 GENERATED，其余为 NOT_STARTED）
     *
     * 异常处理：任何步骤失败均抛出 IllegalStateException，外部捕获后返回错误。
     *
     * @param session 行程生成会话（含需求 JSON）
     * @param day 第 1 天生成记录（含结果 JSON）
     * @param selectedQuote 用户选中的租车报价（可为 null）
     * @return TripGenerateResponse 完整响应
     */
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
            // 组装行程时间线
            assembleTimeline(session, requirement, dailyPlan, selectedQuote);
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
    /**
     * 规范化单日结果 JSON，为旧结果补充 Timeline 时间线。
     *
     * 某些旧版本生成的 DailyPlan 可能缺少 Timeline 数据，或者 Timeline 中的酒店信息
     * 不完整。本方法在返回结果前，对已生成（GENERATED）的记录进行补全操作：
     * 1. 从会话表恢复需求、租车上下文
     * 2. 调用 assembleTimeline() 重新装配时间线（含酒店信息）
     * 3. 将更新后的 DailyPlan 序列化回 JSON 返回
     *
     * 非 GENERATED 状态或反序列化失败时，直接返回原始 resultJson。
     *
     * @param sessionId 生成会话 ID
     * @param day 单日生成记录
     * @return 补全了 Timeline 后的结果 JSON 字符串
     */
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
            assembleTimeline(
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

    /**
     * 组装行程时间线（Timeline）。
     *
     * 为 DailyPlan 的每个景点和活动节点按时间顺序串联为 TimelineNode 序列，
     * 包括出发、交通、景点、餐饮、住宿等节点。
     *
     * 装配流程：
     * 1. 清空现有 Timeline（dailyPlan.setTimeline(null)）
     * 2. 从会话表反序列化 RentalTripContextDTO（租车上下文）
     * 3. 加载前一天已生成的行程并填充酒店数据
     *    （确保次日的 DAY_START 节点从酒店的 STAY_AREA 出发）
     * 4. 调用 TripTimelineAssembler.assemble()：
     *    - 拼装 TimelineNode 序列（DAY_START → TRANSFER → SCENIC → LUNCH_AREA → ...）
     *    - 时间约束：午餐 11:40-12:50，晚餐 17:40-18:50，酒店 20:00-22:30
     *    - 租车场景：Day1 插入 RENTAL_PICKUP 节点
     * 5. 搜索最后一个景点附近的酒店（fillNearbyHotels）
     * 6. 将酒店数据挂到 STAY_AREA 节点上（enrichStayAreaNode），供前端地图渲染
     *
     * @param session AI 行程生成会话（含租车上下文 JSON）
     * @param requirement 用户的旅行需求
     * @param dailyPlan 待装配的当日行程计划
     * @param selectedQuote 用户选中的租车报价选项
     */
    private void assembleTimeline(
            AiTripGenerationSession session,
            TravelRequirementDTO requirement,
            TripPlanDTO.DailyPlan dailyPlan,
            RentalQuoteOptionDTO selectedQuote) {
        dailyPlan.setTimeline(null);
        RentalTripContextDTO rentalTripContext =
                jsonCodec.readNullable(
                        session.getRentalTripContextJson(),  // 从会话中取出租车上下文的 JSON 字符串
                        RentalTripContextDTO.class,           // 反序列化的目标类型
                        "生成会话上下文解析失败");              // 解析失败时的错误提示

        // 加载前一天已生成的行程并填充酒店，使次日起点从酒店出发
        List<TripPlanDTO.DailyPlan> previousDays =
                loadAndEnrichPreviousDays(session, value(dailyPlan.getDay()));

        tripTimelineAssembler.assemble(
                previousDays,
                List.of(dailyPlan),
                new TripTimelineAssembler.TimelineInput(
                        previousDays,
                        List.of(dailyPlan),
                        requirement,
                        selectedQuote,
                        rentalTripContext,
                        List.of(),
                        List.of()));

        // 搜索最后景点附近的酒店
        if (dailyPlan.getNearbyHotels() == null || dailyPlan.getNearbyHotels().isEmpty()) {
            // 根据每天最后一个景点的坐标，搜索附近酒店。
            nearbyHotelService.fillNearbyHotels(List.of(dailyPlan));
        }
        // 将附近酒店数据填充到 STAY_AREA 时间线节点上，供前端地图渲染标记和详情
        nearbyHotelService.enrichStayAreaNode(dailyPlan);
    }

    /**
     * 加载前一天已生成的行程并填充酒店数据。
     *
     * 作用：确保 Timeline 装配时，次日的 DAY_START 节点能从酒店（STAY_AREA）出发，
     * 而非从默认坐标出发，使行程的地理连续性正确。
     *
     * 处理逻辑：
     * 1. 如果 currentDayNo <= 1（第 1 天），返回空列表
     * 2. 从 ai_trip_day_generation 表查询 is_current=1 且 dayNo < currentDayNo 的已生成记录
     * 3. 反序列化每条记录的 resultJson 为 DailyPlan
     * 4. 对缺少酒店数据的旧记录，调用 fillNearbyHotels() 补充填充
     * 5. 调用 enrichStayAreaNode() 将酒店数据挂到 STAY_AREA Timeline 节点上
     *
     * 异常处理：加载失败不阻塞流程，记录警告日志后返回空列表。
     *
     * @param session 生成会话
     * @param currentDayNo 当前天数（加载该天之前的所有天）
     * @return 已填充酒店数据的前几天行程列表
     */
    private List<TripPlanDTO.DailyPlan> loadAndEnrichPreviousDays(
            AiTripGenerationSession session, int currentDayNo) {
        if (currentDayNo <= 1) {
            return List.of();
        }
        try {
            var generatedDays =
                    aiTripDayGenerationService.listCurrentGeneratedBefore(
                            session.getSessionId(), currentDayNo);
            if (generatedDays == null || generatedDays.isEmpty()) {
                return List.of();
            }
            List<TripPlanDTO.DailyPlan> previousDays = new ArrayList<>();
            for (var gen : generatedDays) {
                if (gen.getResultJson() == null || !"GENERATED".equals(gen.getStatus())) {
                    continue;
                }
                TripPlanDTO.DailyPlan day =
                        jsonCodec.readNullable(
                                gen.getResultJson(), TripPlanDTO.DailyPlan.class, "加载前一天行程解析失败");
                if (day != null) {
                    if (day.getNearbyHotels() == null || day.getNearbyHotels().isEmpty()) {
                        nearbyHotelService.fillNearbyHotels(List.of(day));
                    }
                    nearbyHotelService.enrichStayAreaNode(day);
                    previousDays.add(day);
                }
            }
            return previousDays;
        } catch (Exception exception) {
            log.warn("加载前一天行程失败，将使用空列表继续", exception);
            return List.of();
        }
    }

    /**
     * 构建各天生成状态列表。
     *
     * 根据总天数和已生成的第 1 天记录，构建包含所有天状态信息的列表。
     * 第 1 天使用实际生成记录的状态，其余天标记为 NOT_STARTED。
     * 前端使用此列表判断哪些天已生成、哪些天可点击生成、哪些天生成失败需要重试。
     *
     * @param days 总天数（来自 TravelRequirementDTO.days）
     * @param generatedDay 已生成的第 1 天记录
     * @return 各天生成状态列表，按 dayNo 从 1 到 days 排列
     */
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

    /**
     * 汇总每日行程的预算费用。
     *
     * 遍历所有 DailyPlan，累加每人每日的门票、餐饮、交通费用。
     * 酒店费用暂不计算（标记为 null），因为酒店数据来自高德 POI 搜索，
     * 价格准确性有限，仅作为参考。
     *
     * 费用来源说明：
     * - ticketSource / foodSource / transportSource：标记为 "UNAVAILABLE"
     * - hotelSource：标记为 "UNAVAILABLE"
     * - excludesUnknownItems：true（费用为估算值，不保证完整性）
     *
     * @param dailyPlans 已生成的每日行程列表
     * @return TripPlanDTO.BudgetSummary 预算汇总
     */
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

    /**
     * 安全获取 Integer 值，null 时返回 0。
     *
     * 用于预算计算中处理可能为 null 的费用字段。
     */
    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * 获取行程的展示用目的地名称。
     *
     * 按优先级依次尝试：destination > routeRegion > routeCities（用 "-" 拼接）。
     * 例如：目的地"大理" → "大理"；路线城市["大理","丽江"] → "大理-丽江"。
     *
     * @param requirement 结构化需求
     * @return 展示用的目的地名称
     */
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

    /**
     * 发送 SSE 进度事件。
     *
     * 构建 TripGenerateProgressEvent 并通过 SseEmitter 推送给前端。
     * 前端根据 type 字段区分事件类型：
     * - "start"：开始生成
     * - "progress"：进度更新，前端更新进度条和状态文字
     * - "done"：生成完成，data 字段携带完整 TripGenerateResponse
     * - "error"：生成失败，message 字段携带错误信息
     *
     * 发送失败时（如客户端已断开连接），记录警告日志，不抛异常。
     *
     * @param emitter SSE 连接对象
     * @param type 事件类型（start / progress / done / error）
     * @param node 当前节点标识（prepare-session / generating-day1 / ...）
     * @param label 展示给用户的文字（"正在准备行程上下文" / ...）
     * @param progress 进度百分比（0-100）
     * @param data 完成时的完整响应数据（仅 done 事件有值）
     * @param message 错误信息（仅 error 事件有值）
     * @return true 发送成功，false 发送失败（客户端已断开）
     */
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

    /**
     * 投递单日行程生成消息到 RabbitMQ。
     *
     * 使用 Direct Exchange 模式，消息路由到 trip.day.generate.queue，
     * 由 RabbitMqConfig 中的 consumeTripDayGenerate() 方法消费。
     *
     * 消息格式：TripDayGenerateMessage { sessionId, userId, dayNo, requestMode, forceRegenerate, requestId }
     * 消息持久化：Exchange 和 Queue 均为 durable=true，确保服务重启不丢失消息。
     *
     * @param message 单日行程生成消息
     */
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