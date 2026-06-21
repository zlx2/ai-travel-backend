package com.sora.aitravel.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.sora.aitravel.common.result.*;
import org.springframework.web.bind.annotation.*;

@SaCheckLogin
@RestController
@RequestMapping("/api/notes/{id}")
public class NoteInteractionController {
    @PostMapping("/like")
    public R<Void> like(@PathVariable Long id) {
        return ScaffoldResponses.notImplemented();
    }

    @DeleteMapping("/like")
    public R<Void> unlike(@PathVariable Long id) {
        return ScaffoldResponses.notImplemented();
    }

    @PostMapping("/favorite")
    public R<Void> favorite(@PathVariable Long id) {
        return ScaffoldResponses.notImplemented();
    }

    @DeleteMapping("/favorite")
    public R<Void> unfavorite(@PathVariable Long id) {
        return ScaffoldResponses.notImplemented();
    }
}
