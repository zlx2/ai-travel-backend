package com.sora.aitravel.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * 目的地响应 DTO。
 *
 * @param id          目的地 ID
 * @param name        目的地名称
 * @param province    所属省份
 * @param city        所属城市
 * @param longitude   经度
 * @param latitude    纬度
 * @param coverUrl    封面图片 URL
 * @param description 目的地描述
 * @param tags        标签列表
 * @param heat        热度值
 * @param status      状态（0-禁用，1-启用）
 * @param createTime  创建时间
 */
public record DestinationResponse(
        Long id,
        String name,
        String province,
        String city,
        BigDecimal longitude,
        BigDecimal latitude,
        String coverUrl,
        String description,
        List<String> tags,
        Integer heat,
        Integer status,
        String createTime) {}
