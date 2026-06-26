package com.sora.aitravel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** AI 行程生成会话。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_trip_generation_session")
public class AiTripGenerationSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionId;
    private Long userId;
    private String conversationId;
    private String requirementJson;
    private String daySkeletonsJson;
    private String cityProfileJson;
    private String weatherJson;
    private String hotelJson;
    private String status;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
