package com.sora.aitravel.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理后台仪表盘概览响应 DTO。
 *
 * @param userCount 用户总数
 * @param tripCount 旅行计划总数
 * @param noteCount 游记总数
 * @param commentCount 评论总数
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardOverviewResponse {

    private Long userCount;
    private Long tripCount;
    private Long noteCount;
    private Long commentCount;
}
