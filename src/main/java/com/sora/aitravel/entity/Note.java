package com.sora.aitravel.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("note")
public class Note {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String title;
    private String coverUrl;
    private String destination;
    private String summary;

    /** Markdown 正文；一期不支持正文图片上传。 */
    private String content;

    /** 0=草稿，1=已发布，2=已删除；一期不包含审核状态。 */
    private Integer status;

    private Integer viewCount;
    private Integer likeCount;
    private Integer favoriteCount;
    private Integer commentCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 删除游记时必须与 status=2 同步设置。 */
    @TableLogic private Integer deleted;
}
