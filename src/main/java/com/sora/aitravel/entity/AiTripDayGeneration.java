package com.sora.aitravel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** AI 行程按天生成状态和结果。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_trip_day_generation")
public class AiTripDayGeneration {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionId;
    private Long userId;
    private Integer dayNo;
    private Integer generationVersion;
    private String status;
    private Integer isCurrent;
    private String requestMode;
    private String resultJson;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
