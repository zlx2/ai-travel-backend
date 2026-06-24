package com.sora.aitravel.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 聊天响应 DTO。
 *
 * @param reply AI 助手的文本回复内容
 * @param suggestions 推荐的问题或操作建议列表
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiChatResponse {

    private String reply;
    private List<String> suggestions;
}
