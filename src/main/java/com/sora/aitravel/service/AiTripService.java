package com.sora.aitravel.service;

import com.sora.aitravel.dto.request.*;
import com.sora.aitravel.dto.response.*;

/**
 * AI 行程规划服务接口。
 * <p>
 * 提供 AI 驱动的智能旅行行程规划能力，包含需求分析和行程生成两个阶段。
 * </p>
 */
public interface AiTripService {
    /**
     * 分析用户旅行需求。
     * <p>
     * AI 根据用户输入的自然语言需求，解析出目的地、预算、天数、节奏等结构化信息，
     * 并返回分析状态（就绪、需要补充信息、冲突或需要选择目的地）。
     * </p>
     *
     * @param request 旅行需求分析请求
     * @return 分析结果，包含结构化需求信息和分析状态
     */
    TripAnalyzeResponse analyze(TripAnalyzeRequest request);

    /**
     * 生成完整的旅行行程。
     * <p>
     * 在需求分析完成后，AI 根据分析结果生成每日的详细行程安排。
     * </p>
     *
     * @param request 行程生成请求
     * @return 生成的完整行程方案
     */
    TripGenerateResponse generate(TripGenerateRequest request);
}
