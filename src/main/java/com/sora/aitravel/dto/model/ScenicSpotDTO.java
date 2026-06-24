package com.sora.aitravel.dto.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScenicSpotDTO {

    private String name;
    private String area;
    private String reason;
    private String suggestedDuration;
    private Boolean suitableForSelfDrive;
}
