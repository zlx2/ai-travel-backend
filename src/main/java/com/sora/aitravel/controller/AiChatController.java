package com.sora.aitravel.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.sora.aitravel.common.result.*;
import com.sora.aitravel.dto.request.AiChatRequest;
import com.sora.aitravel.dto.response.AiChatResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@SaCheckLogin
@RestController
@RequestMapping("/api/ai")
public class AiChatController {
    @PostMapping("/chat")
    public R<AiChatResponse> chat(@Valid @RequestBody AiChatRequest request) {
        return ScaffoldResponses.notImplemented();
    }
}
