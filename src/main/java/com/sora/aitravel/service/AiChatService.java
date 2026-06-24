package com.sora.aitravel.service;

import com.sora.aitravel.dto.request.AiChatRequest;
import com.sora.aitravel.dto.response.AiChatResponse;

/**
 * AI 对话服务接口。
 *
 * <p>提供与 AI 进行自由对话聊天的功能，用于行程规划前的需求沟通。
 */
public interface AiChatService {
    /**
     * 发送消息给 AI 并获取回复。
     *
     * @param request 对话请求，包含消息内容和会话上下文
     * @return AI 的回复内容
     */
    AiChatResponse chat(AiChatRequest request);
}
