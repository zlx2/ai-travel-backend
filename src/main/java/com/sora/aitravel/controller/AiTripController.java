package com.sora.aitravel.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.sora.aitravel.common.result.*;
import com.sora.aitravel.common.utils.LoginUserUtils;
import com.sora.aitravel.dto.request.*;
import com.sora.aitravel.dto.response.*;
import com.sora.aitravel.workflow.analyze.AnalyzeWorkflowContext;
import com.sora.aitravel.workflow.analyze.TripAnalyzeWorkflow;
import com.sora.aitravel.workflow.generate.GenerateWorkflowContext;
import com.sora.aitravel.workflow.generate.TripGenerateWorkflow;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.Map;
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

    private final TripGenerateWorkflow tripGenerateWorkflow;
    private final TripAnalyzeWorkflow tripAnalyzeWorkflow;

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
        GenerateWorkflowContext context = new GenerateWorkflowContext();
        context.setUserId(LoginUserUtils.getUserId());
        context.setRequest(request);
        return R.ok(tripGenerateWorkflow.execute(context).getResult());
    }

    @PostMapping(value = "/generate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generateStream(@Valid @RequestBody TripGenerateRequest request) {
        SseEmitter emitter = new SseEmitter(300_000L);
        Long userId = LoginUserUtils.getUserId();
        CompletableFuture.runAsync(
                () -> {
                    GenerateWorkflowContext context = new GenerateWorkflowContext();
                    context.setUserId(userId);
                    context.setRequest(request);
                    try {
                        sendProgress(emitter, "start", "start", "开始生成行程", 1, null, null);
                        GenerateWorkflowContext result =
                                tripGenerateWorkflow.executeWithProgress(
                                        context,
                                        (node, progress) ->
                                                sendProgress(
                                                        emitter,
                                                        "progress",
                                                        node,
                                                        progressLabel(node),
                                                        progress,
                                                        null,
                                                        null));
                        sendProgress(
                                emitter, "done", "done", "行程生成完成", 100, result.getResult(), null);
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
                        Map.entry("trip-skeleton", "正在规划每日主题"),
                        Map.entry("city-data-profile", "正在整理城市景点资料"),
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
