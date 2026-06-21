package com.sora.aitravel.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("admin_operation_log")
public class AdminOperationLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long adminId;
    private String operation;
    private String targetType;
    private Long targetId;
    private String content;
    private String ip;
    private LocalDateTime createTime;
}
