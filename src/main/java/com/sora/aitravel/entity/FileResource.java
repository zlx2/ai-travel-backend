package com.sora.aitravel.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("file_resource")
public class FileResource {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 一期只允许 avatar 或 note_cover。 */
    private String bizType;

    private String fileName;
    private String objectKey;
    private String url;
    private String contentType;
    private Long size;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic private Integer deleted;
}
