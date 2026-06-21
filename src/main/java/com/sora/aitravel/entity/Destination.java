package com.sora.aitravel.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("destination")
public class Destination {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String province;
    private String city;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String coverUrl;
    private String description;

    /** 目的地标签数组的 JSON 文本。 */
    private String tagsJson;

    private Integer heat;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic private Integer deleted;
}
