package com.sora.aitravel.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.sora.aitravel.common.result.*;
import com.sora.aitravel.dto.request.*;
import com.sora.aitravel.dto.response.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 旅行计划控制器。
 *
 * <p>接口前缀：/api/trips
 *
 * <p>请求方式：RESTful
 *
 * <p>权限要求：所有接口均需登录（@SaCheckLogin）
 */
@SaCheckLogin
@RestController
@RequestMapping("/api/trips")
public class TripController {
    /**
     * 保存新的旅行计划。
     *
     * @param request 包含行程基本信息（出发地、目的地、天数、预算等）的保存请求
     * @return 保存成功返回新行程的 ID（IdResponse）
     */
    @PostMapping
    public R<IdResponse> save(@Valid @RequestBody SaveTripRequest request) {
        return ScaffoldResponses.notImplemented();
    }

    /**
     * 分页查询当前登录用户的旅行计划列表。
     *
     * @param pageNum 页码，默认 1
     * @param pageSize 每页条数，默认 10
     * @param keyword 搜索关键字（可选，模糊匹配标题）
     * @param destination 目的地筛选（可选）
     * @return 分页的旅行计划列表（TripListItemResponse）
     */
    @GetMapping("/my")
    public R<PageResult<TripListItemResponse>> listMy(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String destination) {
        return ScaffoldResponses.notImplemented();
    }

    /**
     * 获取指定旅行计划的详情。
     *
     * @param id 旅行计划 ID
     * @return 旅行计划的详细信息（TripDetailResponse）
     */
    @GetMapping("/{id}")
    public R<TripDetailResponse> detail(@PathVariable Long id) {
        return ScaffoldResponses.notImplemented();
    }

    /**
     * 更新指定旅行计划。
     *
     * @param id 旅行计划 ID
     * @param request 包含待更新字段的请求体
     * @return 无返回内容，更新成功即返回成功响应
     */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody UpdateTripRequest request) {
        return ScaffoldResponses.notImplemented();
    }

    /**
     * 删除指定旅行计划。
     *
     * @param id 旅行计划 ID
     * @return 无返回内容，删除成功即返回成功响应
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        return ScaffoldResponses.notImplemented();
    }
}
