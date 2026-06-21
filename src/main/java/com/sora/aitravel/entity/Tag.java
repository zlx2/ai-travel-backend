package com.sora.aitravel.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("tag")
public class Tag {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 1=游记标签，2=偏好标签，3=目的地标签。 */
    private Integer type;

    /** 0=禁用，1=启用。 */
    private Integer status;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic private Integer deleted;
}
