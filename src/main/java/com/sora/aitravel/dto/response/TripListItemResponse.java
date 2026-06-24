package com.sora.aitravel.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 旅行计划列表项响应 DTO（列表展示用）。
 *
 * @param id 行程 ID
 * @param title 行程标题
 * @param departure 出发地
 * @param destination 目的地
 * @param days 行程天数
 * @param budget 预算金额
 * @param summary 行程摘要
 * @param coverUrl 封面图片 URL
 * @param createTime 创建时间
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TripListItemResponse {

    private Long id;
    private String title;
    private String departure;
    private String destination;
    private Integer days;
    private Integer budget;
    private String summary;
    private String coverUrl;
    private String createTime;
}
