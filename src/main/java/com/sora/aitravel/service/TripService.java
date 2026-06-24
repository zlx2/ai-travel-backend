package com.sora.aitravel.service;

import com.sora.aitravel.common.result.*;
import com.sora.aitravel.dto.request.*;
import com.sora.aitravel.dto.response.*;

/**
 * 行程服务接口。
 *
 * <p>提供旅行行程的完整 CRUD 功能，包括保存行程、查询我的行程列表、 获取行程详情、更新和删除行程。
 */
public interface TripService {
    /**
     * 保存行程（AI 生成的行程或手动创建的行程）。
     *
     * @param request 保存行程请求
     * @return 保存后的行程 ID
     */
    Long save(SaveTripRequest request);

    /**
     * 分页查询当前用户创建的行程列表。
     *
     * @param pageNum 页码（从 1 开始）
     * @param pageSize 每页条数
     * @param keyword 搜索关键词
     * @param destination 目的地筛选
     * @return 我的行程分页结果
     */
    PageResult<TripListItemResponse> listMy(
            Integer pageNum, Integer pageSize, String keyword, String destination);

    /**
     * 获取行程详情。
     *
     * @param id 行程 ID
     * @return 行程详细信息，包含每日安排
     */
    TripDetailResponse getDetail(Long id);

    /**
     * 更新行程信息。
     *
     * @param id 行程 ID
     * @param request 更新行程请求
     */
    void update(Long id, UpdateTripRequest request);

    /**
     * 删除行程。
     *
     * @param id 行程 ID
     */
    void delete(Long id);
}
