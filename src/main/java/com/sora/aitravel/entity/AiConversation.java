package com.sora.aitravel.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("ai_conversation")
public class AiConversation {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String conversationId;
    private Long userId;

    /** TRIP_ANALYZE、TRIP_GENERATE 或 AI_CHAT。 */
    private String scene;

    /** 1=正常，2=已结束，3=已过期。 */
    private Integer status;

    /** 多轮追问所需的上下文快照；Redis 为热数据，MySQL 用于恢复和追踪。 */
    private String contextJson;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
