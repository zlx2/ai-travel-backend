package com.sora.aitravel.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("trip")
public class Trip {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String conversationId;
    private String title;
    private String departure;
    private String destination;
    private Integer days;
    private Integer budget;

    /** 用户旅行偏好数组的 JSON 文本。 */
    private String preferencesJson;

    /** AI 分析确认后的 TravelRequirement JSON。 */
    private String requirementJson;

    /** 完整 TripPlan JSON，行程详情问 AI 时作为上下文。 */
    private String tripPlanJson;

    private String summary;
    private String coverUrl;
    private Integer source; // 1=AI 生成，2=手动创建，3=模板复制；一期默认 1。
    private Integer status; // 1=正常，2=已删除。
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 删除行程时必须与 status=2 同步设置。 */
    @TableLogic private Integer deleted;
}
