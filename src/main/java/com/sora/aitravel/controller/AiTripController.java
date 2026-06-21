package com.sora.aitravel.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.sora.aitravel.common.result.*;
import com.sora.aitravel.dto.request.*;
import com.sora.aitravel.dto.response.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@SaCheckLogin
@RestController
@RequestMapping("/api/ai/trips")
public class AiTripController {
    @PostMapping("/analyze")
    public R<TripAnalyzeResponse> analyze(@Valid @RequestBody TripAnalyzeRequest request) {
        return ScaffoldResponses.notImplemented();
    }

    @PostMapping("/generate")
    public R<TripGenerateResponse> generate(@Valid @RequestBody TripGenerateRequest request) {
        return ScaffoldResponses.notImplemented();
    }
}
