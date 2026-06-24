package com.sora.aitravel.controller;

import com.sora.aitravel.common.result.*;
import com.sora.aitravel.dto.response.DestinationResponse;
import java.util.List;
import org.springframework.web.bind.annotation.*;

/**
 * 目的地控制器。
 *
 * <p>接口前缀：/api/destinations
 *
 * <p>请求方式：RESTful
 *
 * <p>权限要求：所有接口均为公开，无需登录
 */
@RestController
@RequestMapping("/api/destinations")
public class DestinationController {
    /**
     * 分页查询目的地列表（公开接口）。
     *
     * @param keyword 搜索关键字（可选，模糊匹配名称）
     * @param pageNum 页码，默认 1
     * @param pageSize 每页条数，默认 10
     * @return 分页的目的地列表（DestinationResponse）
     */
    @GetMapping
    public R<PageResult<DestinationResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return ScaffoldResponses.notImplemented();
    }

    /**
     * 获取热门目的地列表（公开接口）。
     *
     * @return 热门目的地列表（DestinationResponse）
     */
    @GetMapping("/hot")
    public R<List<DestinationResponse>> hot() {
        return ScaffoldResponses.notImplemented();
    }
}
