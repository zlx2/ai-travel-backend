package com.sora.aitravel.dto.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 景点推荐候选。
 *
 * <p>由真实 POI 查询结果或 AI 评估后的真实候选生成，不承载伪造景点。
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
