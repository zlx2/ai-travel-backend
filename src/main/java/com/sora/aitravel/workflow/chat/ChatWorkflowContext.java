package com.sora.aitravel.workflow.chat;

import com.sora.aitravel.dto.request.AiChatRequest;
import com.sora.aitravel.dto.response.AiChatResponse;
import lombok.Data;

@Data
public class ChatWorkflowContext {
    private Long userId;
    private AiChatRequest request;
    private String tripPlanJson;
    private String prompt;
    private String rawModelResponse;
    private AiChatResponse result;
}
