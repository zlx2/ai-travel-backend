package com.sora.aitravel.service;

import com.sora.aitravel.common.result.PageResult;
import com.sora.aitravel.dto.response.DestinationResponse;
import java.util.List;

/**
 * 目的地服务接口。
 *
 * <p>提供旅行目的地的搜索查询和热门目的地推荐功能。
 */
public interface DestinationService {
    /**
     * 分页搜索目的地列表。
     *
     * @param keyword 搜索关键词，可为 null 或空字符串表示查询全部
     * @param pageNum 页码（从 1 开始）
     * @param pageSize 每页条数
     * @return 目的地分页结果
     */
    PageResult<DestinationResponse> list(String keyword, Integer pageNum, Integer pageSize);

    /**
     * 获取热门目的地列表。
     *
     * @return 热门目的地列表
     */
    List<DestinationResponse> hot();
}
