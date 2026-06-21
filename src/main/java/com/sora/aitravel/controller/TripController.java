package com.sora.aitravel.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.sora.aitravel.common.result.*;
import com.sora.aitravel.dto.request.*;
import com.sora.aitravel.dto.response.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@SaCheckLogin
@RestController
@RequestMapping("/api/trips")
public class TripController {
    @PostMapping
    public R<IdResponse> save(@Valid @RequestBody SaveTripRequest request) {
        return ScaffoldResponses.notImplemented();
    }

    @GetMapping("/my")
    public R<PageResult<TripListItemResponse>> listMy(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String destination) {
        return ScaffoldResponses.notImplemented();
    }

    @GetMapping("/{id}")
    public R<TripDetailResponse> detail(@PathVariable Long id) {
        return ScaffoldResponses.notImplemented();
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody UpdateTripRequest request) {
        return ScaffoldResponses.notImplemented();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        return ScaffoldResponses.notImplemented();
    }
}
