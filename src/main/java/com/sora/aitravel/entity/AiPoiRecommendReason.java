package com.sora.aitravel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** POI 推荐理由资产，用于 AI 生成缓存和人工审核。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_poi_recommend_reason")
public class AiPoiRecommendReason {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String poiSource;
    private String poiId;
    private String poiName;
    private String city;
    private String poiType;
    private String promptVersion;
    private String reason;
    private String status;
    private String modelName;
    private Long reviewerId;
    private LocalDateTime reviewedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
