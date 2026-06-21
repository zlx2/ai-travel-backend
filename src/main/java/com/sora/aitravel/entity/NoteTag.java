package com.sora.aitravel.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 游记标签关联实体类（多对多关联表）。
 * <p>
 * 对应数据库表 {@code note_tag}，用于建立游记（note）与标签（tag）之间的多对多关系。
 * 每条记录表示一篇游记被赋予了某个标签，通过该表可快速查询某篇游记的所有标签，
 * 或某个标签下的所有游记。
 * </p>
 *
 * <table border="1">
 *   <caption>字段与数据库列映射</caption>
 *   <tr><th>字段</th><th>数据库列</th><th>说明</th></tr>
 *   <tr><td>id</td><td>id</td><td>主键，自增</td></tr>
 *   <tr><td>noteId</td><td>note_id</td><td>游记 ID，关联 note.id</td></tr>
 *   <tr><td>tagId</td><td>tag_id</td><td>标签 ID，关联 tag.id</td></tr>
 *   <tr><td>createTime</td><td>create_time</td><td>关联创建时间</td></tr>
 * </table>
 */
@Data
@TableName("note_tag")
public class NoteTag {
    /** 主键 ID，自增。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 游记 ID，关联 note.id。 */
    private Long noteId;

    /** 标签 ID，关联 tag.id。 */
    private Long tagId;

    /** 关联记录创建时间。 */
    private LocalDateTime createTime;
}
