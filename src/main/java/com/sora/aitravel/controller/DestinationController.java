package com.sora.aitravel.controller;

import com.sora.aitravel.common.result.*;
import com.sora.aitravel.dto.response.DestinationResponse;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/destinations")
public class DestinationController {
    @GetMapping
    public R<PageResult<DestinationResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return ScaffoldResponses.notImplemented();
    }

    @GetMapping("/hot")
    public R<List<DestinationResponse>> hot() {
        return ScaffoldResponses.notImplemented();
    }
}
