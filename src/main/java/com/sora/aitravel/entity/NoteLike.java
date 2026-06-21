package com.sora.aitravel.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("note_like")
public class NoteLike {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long noteId;
    private Long userId;
    private LocalDateTime createTime;
}
