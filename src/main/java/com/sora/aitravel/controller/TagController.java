package com.sora.aitravel.controller;

import com.sora.aitravel.common.result.*;
import com.sora.aitravel.dto.response.TagResponse;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tags")
public class TagController {
    @GetMapping
    public R<List<TagResponse>> list(@RequestParam(required = false) Integer type) {
        return ScaffoldResponses.notImplemented();
    }
}
