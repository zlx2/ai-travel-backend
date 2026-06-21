package com.sora.aitravel.dto.response;

/**
 * 旅行计划列表项响应 DTO（列表展示用）。
 *
 * @param id          行程 ID
 * @param title       行程标题
 * @param departure   出发地
 * @param destination 目的地
 * @param days        行程天数
 * @param budget      预算金额
 * @param summary     行程摘要
 * @param coverUrl    封面图片 URL
 * @param createTime  创建时间
 */
public record TripListItemResponse(
        Long id,
        String title,
        String departure,
        String destination,
        Integer days,
        Integer budget,
        String summary,
        String coverUrl,
        String createTime) {}
