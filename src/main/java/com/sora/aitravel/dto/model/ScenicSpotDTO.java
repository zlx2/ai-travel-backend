package com.sora.aitravel.dto.model;

/**
 * 景点推荐候选。
 *
 * <p>当前可由假数据节点生成，后续可替换为数据库、高德 POI 或 AI 评估后的真实推荐。
 *
 * @param name 景点名称
 * @param area 所在区域
 * @param reason 推荐原因
 * @param suggestedDuration 建议游玩时长
 * @param suitableForSelfDrive 是否适合自驾串联
 */
public record ScenicSpotDTO(
        String name,
        String area,
        String reason,
        String suggestedDuration,
        Boolean suitableForSelfDrive) {}
