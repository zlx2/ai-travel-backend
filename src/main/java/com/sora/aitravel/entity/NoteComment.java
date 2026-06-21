package com.sora.aitravel.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("note_comment")
public class NoteComment {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long noteId;
    private Long userId;
    private String content;

    /** 1=正常，2=已删除。 */
    private Integer status;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 删除评论时必须与 status=2 同步设置。 */
    @TableLogic private Integer deleted;
}
