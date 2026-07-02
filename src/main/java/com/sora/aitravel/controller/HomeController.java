package com.sora.aitravel.controller;

import com.sora.aitravel.common.result.*;
import com.sora.aitravel.dto.response.HomeResponse;
import com.sora.aitravel.service.HomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 首页控制器。
 *
 * <p>接口前缀：/home，全局 /api 前缀由 server.servlet.context-path 配置。
 *
 * <p>请求方式：GET
 *
 * <p>权限要求：公开接口，无需登录
 */
@RestController
@RequestMapping("/home")
@RequiredArgsConstructor
public class HomeController {
    private final HomeService homeService;

    /**
     * 获取首页数据（公开接口）。
     *
     * <p>包含热门目的地、热门游记和热门标签等推荐内容。
     *
     * @return 首页聚合数据（HomeResponse）
     */
    @GetMapping
    public R<HomeResponse> home() {
        return R.ok(homeService.aggregate());
    }
}
