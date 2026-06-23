package com.sora.aitravel.dto.model;

/**
 * 住宿区域推荐。
 *
 * <p>AI 旅行方案只推荐适合住宿的区域和预算范围，不直接完成酒店预订。
 *
 * @param area 区域名称
 * @param reason 推荐原因
 * @param priceRange 参考价格区间
 */
public record HotelAreaDTO(String area, String reason, String priceRange) {}
