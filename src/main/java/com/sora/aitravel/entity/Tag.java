package com.sora.aitravel.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 标签实体类。
 * <p>
 * 对应数据库表 {@code tag}，存储系统标签数据。标签分为三种类型：游记标签
 * （用于游记内容分类）、偏好标签（用于用户旅行偏好选择）和目的地标签
 * （用于目的地特征标记）。标签可以被启用或禁用。该表通过逻辑删除标记
 * （deleted）实现软删除。
 * </p>
 *
 * <table border="1">
 *   <caption>字段与数据库列映射</caption>
 *   <tr><th>字段</th><th>数据库列</th><th>说明</th></tr>
 *   <tr><td>id</td><td>id</td><td>主键，自增</td></tr>
 *   <tr><td>name</td><td>name</td><td>标签名称</td></tr>
 *   <tr><td>type</td><td>type</td><td>标签类型：1-游记标签，2-偏好标签，3-目的地标签</td></tr>
 *   <tr><td>status</td><td>status</td><td>状态：0-禁用，1-启用</td></tr>
 *   <tr><td>createTime</td><td>create_time</td><td>记录创建时间</td></tr>
 *   <tr><td>updateTime</td><td>update_time</td><td>记录更新时间</td></tr>
 *   <tr><td>deleted</td><td>deleted</td><td>逻辑删除标记：0-未删除，1-已删除</td></tr>
 * </table>
 */
@Data
@TableName("tag")
public class Tag {
    /** 主键 ID，自增。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 标签名称，如"美食"、"自然风光"。 */
    private String name;

    /** 标签类型：1=游记标签，2=偏好标签，3=目的地标签。 */
    private Integer type;

    /** 状态：0=禁用，1=启用。 */
    private Integer status;

    /** 记录创建时间。 */
    private LocalDateTime createTime;

    /** 记录更新时间。 */
    private LocalDateTime updateTime;

    /** 逻辑删除标记：0=未删除，1=已删除。 */
    @TableLogic
    private Integer deleted;
}
