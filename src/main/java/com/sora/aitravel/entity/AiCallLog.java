package com.sora.aitravel.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("ai_call_log")
public class AiCallLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String conversationId;
    private String scene;
    private String modelName;

    /** 仅记录必要摘要并对敏感信息脱敏，避免保存过大的完整 Prompt。 */
    private String requestJson;

    private String responseJson;

    /** 0=失败，1=成功；失败调用必须落库。 */
    private Integer success;

    private String errorMessage;
    private Long durationMs;
    private LocalDateTime createTime;
}
