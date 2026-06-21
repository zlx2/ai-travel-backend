package com.sora.aitravel.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.sora.aitravel.common.result.*;
import com.sora.aitravel.dto.request.*;
import com.sora.aitravel.dto.response.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notes")
public class NoteController {
    @GetMapping
    public R<PageResult<NoteListItemResponse>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String destination,
            @RequestParam(required = false) Long tagId,
            @RequestParam(defaultValue = "latest") String sort) {
        return ScaffoldResponses.notImplemented();
    }

    @SaCheckLogin
    @PostMapping
    public R<IdResponse> create(@Valid @RequestBody CreateNoteRequest request) {
        return ScaffoldResponses.notImplemented();
    }

    @GetMapping("/{id}")
    public R<NoteDetailResponse> detail(@PathVariable Long id) {
        return ScaffoldResponses.notImplemented();
    }

    @SaCheckLogin
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody UpdateNoteRequest request) {
        return ScaffoldResponses.notImplemented();
    }

    @SaCheckLogin
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        return ScaffoldResponses.notImplemented();
    }
}
