package com.sora.aitravel.dto.response;

/**
 * 管理后台仪表盘概览响应 DTO。
 *
 * @param userCount    用户总数
 * @param tripCount    旅行计划总数
 * @param noteCount    游记总数
 * @param commentCount 评论总数
 */
public record DashboardOverviewResponse(
        Long userCount, Long tripCount, Long noteCount, Long commentCount) {}
