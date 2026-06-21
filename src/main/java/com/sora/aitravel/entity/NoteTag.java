package com.sora.aitravel.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("note_tag")
public class NoteTag {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long noteId;
    private Long tagId;
    private LocalDateTime createTime;
}
