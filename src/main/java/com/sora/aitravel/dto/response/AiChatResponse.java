package com.sora.aitravel.dto.response;

import java.util.List;

/**
 * AI 聊天响应 DTO。
 *
 * @param reply       AI 助手的文本回复内容
 * @param suggestions 推荐的问题或操作建议列表
 */
public record AiChatResponse(String reply, List<String> suggestions) {}
