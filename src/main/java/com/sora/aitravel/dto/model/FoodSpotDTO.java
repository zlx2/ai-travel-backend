package com.sora.aitravel.dto.model;

/**
 * 美食推荐候选。
 *
 * <p>表示行程生成前准备好的餐饮上下文，不等同于餐厅预订或实时排队信息。
 *
 * @param name 美食或餐饮区域名称
 * @param area 所在区域
 * @param specialty 特色
 * @param reason 推荐原因
 */
public record FoodSpotDTO(String name, String area, String specialty, String reason) {}
