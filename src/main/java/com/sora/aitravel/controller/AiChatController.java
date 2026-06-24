package com.sora.aitravel.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.sora.aitravel.common.result.*;
import com.sora.aitravel.dto.request.AiChatRequest;
import com.sora.aitravel.dto.response.AiChatResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * AI 智能聊天控制器。
 *
 * <p>接口前缀：/api/ai
 *
 * <p>请求方式：POST
 *
 * <p>权限要求：所有接口均需登录（@SaCheckLogin）
 */
@SaCheckLogin
@RestController
@RequestMapping("/api/ai")
public class AiChatController {
    /**
     * 与 AI 助手进行对话（需登录）。
     *
     * <p>一期仅支持 TRIP 模式，即基于用户已保存的旅行计划进行对话。
     *
     * @param request 包含对话模式、旅行计划 ID 和用户消息的请求体
     * @return AI 助手的回复内容和建议列表（AiChatResponse）
     */
    @PostMapping("/chat")
    public R<AiChatResponse> chat(@Valid @RequestBody AiChatRequest request) {
        return ScaffoldResponses.notImplemented();
    }
}
