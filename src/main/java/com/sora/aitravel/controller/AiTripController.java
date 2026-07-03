package com.sora.aitravel.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.sora.aitravel.common.result.R;
import com.sora.aitravel.common.utils.LoginUserUtils;
import com.sora.aitravel.dto.request.TripAnalyzeRequest;
import com.sora.aitravel.dto.request.TripGenerateRequest;
import com.sora.aitravel.dto.response.TripAnalyzeResponse;
import com.sora.aitravel.dto.response.TripGenerateDayResponse;
import com.sora.aitravel.dto.response.TripGenerateResponse;
import com.sora.aitravel.dto.response.TripGenerateSessionResponse;
import com.sora.aitravel.service.impl.AiTripApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@SaCheckLogin
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/trips")
public class AiTripController {

    private final AiTripApplicationService aiTripApplicationService;

    @PostMapping("/analyze")
    public R<TripAnalyzeResponse> analyze(@Valid @RequestBody TripAnalyzeRequest request) {
        return R.ok(aiTripApplicationService.analyze(LoginUserUtils.getUserId(), request));
    }

    @PostMapping("/generate")
    public R<TripGenerateResponse> generate(@Valid @RequestBody TripGenerateRequest request) {
        return R.ok(aiTripApplicationService.generate(LoginUserUtils.getUserId(), request));
    }

    @PostMapping("/generate/session")
    public R<TripGenerateSessionResponse> prepareSession(
            @Valid @RequestBody TripGenerateRequest request) {
        return R.ok(aiTripApplicationService.prepareSession(LoginUserUtils.getUserId(), request));
    }

    @PostMapping("/generate/session/{sessionId}/days/{dayNo}")
    public R<TripGenerateDayResponse> generateDay(
            @PathVariable String sessionId,
            @PathVariable Integer dayNo,
            @RequestParam(defaultValue = "USER") String requestMode,
            @RequestParam(defaultValue = "false") boolean forceRegenerate,
            @RequestParam(defaultValue = "false") boolean prefetchNext,
            @RequestParam(required = false) String revisionText) {
        return R.ok(
                aiTripApplicationService.generateDay(
                        sessionId,
                        dayNo,
                        requestMode,
                        forceRegenerate,
                        prefetchNext,
                        revisionText));
    }

    @PostMapping(value = "/generate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generateStream(@Valid @RequestBody TripGenerateRequest request) {
        return aiTripApplicationService.generateStream(LoginUserUtils.getUserId(), request);
    }
}
