package com.sora.aitravel.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 游记评论实体类。
 *
 * <p>对应数据库表 {@code note_comment}，存储用户对游记的评论内容。 每条评论关联一篇游记（noteId）和一个用户（userId），包含评论文本。
 * 评论支持软删除，删除时需同步将 status 置为 2。
 *
 * <table border="1">
 *   <caption>字段与数据库列映射</caption>
 *   <tr><th>字段</th><th>数据库列</th><th>说明</th></tr>
 *   <tr><td>id</td><td>id</td><td>主键，自增</td></tr>
 *   <tr><td>noteId</td><td>note_id</td><td>被评论的游记 ID，关联 note.id</td></tr>
 *   <tr><td>userId</td><td>user_id</td><td>评论用户 ID，关联 sys_user.id</td></tr>
 *   <tr><td>content</td><td>content</td><td>评论正文内容</td></tr>
 *   <tr><td>status</td><td>status</td><td>状态：1-正常，2-已删除</td></tr>
 *   <tr><td>createTime</td><td>create_time</td><td>评论创建时间</td></tr>
 *   <tr><td>updateTime</td><td>update_time</td><td>记录更新时间</td></tr>
 *   <tr><td>deleted</td><td>deleted</td><td>逻辑删除标记：0-未删除，1-已删除</td></tr>
 * </table>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("note_comment")
public class NoteComment {
    /** 主键 ID，自增。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 被评论的游记 ID，关联 note.id。 */
    private Long noteId;

    /** 评论用户 ID，关联 sys_user.id。 */
    private Long userId;

    /** 评论正文内容，支持纯文本。 */
    private String content;

    /** 评论状态：1=正常，2=已删除。 */
    private Integer status;

    /** 评论创建时间。 */
    private LocalDateTime createTime;

    /** 记录更新时间。 */
    private LocalDateTime updateTime;

    /** 删除评论时必须与 status=2 同步设置。 */
    @TableLogic private Integer deleted;
}
