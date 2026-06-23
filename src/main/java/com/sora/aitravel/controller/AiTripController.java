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
import org.springframework.web.bind.annotation.*;

/**
 * AI 智能旅行规划控制器（AI 分析+生成行程）。
 * <p>接口前缀：/api/ai/trips</p>
 * <p>请求方式：POST</p>
 * <p>权限要求：所有接口均需登录（@SaCheckLogin）</p>
 */
@SaCheckLogin
@RestController
@RequestMapping("/api/ai/trips")
public class AiTripController {

    private final TripGenerateWorkflow tripGenerateWorkflow;
    private final TripAnalyzeWorkflow tripAnalyzeWorkflow;

    public AiTripController(
            TripGenerateWorkflow tripGenerateWorkflow, TripAnalyzeWorkflow tripAnalyzeWorkflow) {
        this.tripGenerateWorkflow = tripGenerateWorkflow;
        this.tripAnalyzeWorkflow = tripAnalyzeWorkflow;
    }

    /**
     * AI 分析用户旅行需求（需登录）。
     * <p>支持两种模式：用户主动输入模糊需求并提取结构化信息，或对已推荐的目的地确认后返回分析结果。</p>
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
}
