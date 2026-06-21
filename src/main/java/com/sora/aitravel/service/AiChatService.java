package com.sora.aitravel.service;

import com.sora.aitravel.dto.request.AiChatRequest;
import com.sora.aitravel.dto.response.AiChatResponse;

public interface AiChatService {
    AiChatResponse chat(AiChatRequest request);
}
