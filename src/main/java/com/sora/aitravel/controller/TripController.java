package com.sora.aitravel.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.sora.aitravel.common.result.IdResponse;
import com.sora.aitravel.common.result.PageResult;
import com.sora.aitravel.common.result.R;
import com.sora.aitravel.dto.request.SaveTripRequest;
import com.sora.aitravel.dto.request.UpdateTripRequest;
import com.sora.aitravel.dto.response.TripDetailResponse;
import com.sora.aitravel.dto.response.TripListItemResponse;
import com.sora.aitravel.service.TripService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    /**
     * 保存新的旅行计划。
     *
     * @param request 包含行程基本信息的保存请求
     * @return 保存成功返回新行程的 ID
     */
    @PostMapping
    public R<IdResponse> save(@Valid @RequestBody SaveTripRequest request) {
        Long id = tripService.save(request);
        return R.ok(new IdResponse(id));
    }

    /**
     * 分页查询当前登录用户的旅行计划列表。
     *
     * @param pageNum 页码，默认 1
     * @param pageSize 每页条数，默认 10
     * @param keyword 搜索关键字
     * @param destination 目的地筛选
     * @return 分页的旅行计划列表
     */
    @GetMapping("/my")
    public R<PageResult<TripListItemResponse>> listMy(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String destination) {
        return R.ok(tripService.listMy(pageNum, pageSize, keyword, destination));
    }

    /**
     * 获取指定旅行计划的详情。
     *
     * @param id 旅行计划 ID
     * @return 旅行计划详情
     */
    @GetMapping("/{id}")
    public R<TripDetailResponse> detail(@PathVariable Long id) {
        return R.ok(tripService.getDetail(id));
    }

    /**
     * 更新指定旅行计划。
     *
     * @param id 旅行计划 ID
     * @param request 包含待更新字段的请求体
     * @return 更新成功响应
     */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody UpdateTripRequest request) {
        tripService.update(id, request);
        return R.ok();
    }

    /**
     * 删除指定旅行计划。
     *
     * @param id 旅行计划 ID
     * @return 删除成功响应
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        tripService.delete(id);
        return R.ok();
    }
}
