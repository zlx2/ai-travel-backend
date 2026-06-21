package com.sora.aitravel.dto.response;

import java.util.List;

public record AiChatResponse(String reply, List<String> suggestions) {}
