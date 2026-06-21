package com.sora.aitravel.controller;

import com.sora.aitravel.common.result.*;
import com.sora.aitravel.dto.response.HomeResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/home")
public class HomeController {
    @GetMapping
    public R<HomeResponse> home() {
        return ScaffoldResponses.notImplemented();
    }
}
