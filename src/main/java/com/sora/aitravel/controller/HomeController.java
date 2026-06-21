package com.sora.aitravel.controller;

import com.sora.aitravel.common.result.*;
import com.sora.aitravel.dto.response.HomeResponse;
import org.springframework.web.bind.annotation.*;

/**
 * 首页控制器。
 * <p>接口前缀：/api/home</p>
 * <p>请求方式：GET</p>
 * <p>权限要求：公开接口，无需登录</p>
 */
@RestController
@RequestMapping("/api/home")
public class HomeController {
    /**
     * 获取首页数据（公开接口）。
     * <p>包含热门目的地、热门游记和热门标签等推荐内容。</p>
     *
     * @return 首页聚合数据（HomeResponse）
     */
    @GetMapping
    public R<HomeResponse> home() {
        return ScaffoldResponses.notImplemented();
    }
}
