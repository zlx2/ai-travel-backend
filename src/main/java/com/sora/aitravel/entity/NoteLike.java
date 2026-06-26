package com.sora.aitravel.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 游记点赞实体类。
 *
 * <p>对应数据库表 {@code note_like}，记录用户对游记的点赞行为。 每条记录表示一个用户对一篇游记进行了点赞。通过用户 ID 和游记 ID 可唯一确定
 * 一条点赞记录，因此业务上需确保同一用户对同一游记只能点赞一次。
 *
 * <table border="1">
 *   <caption>字段与数据库列映射</caption>
 *   <tr><th>字段</th><th>数据库列</th><th>说明</th></tr>
 *   <tr><td>id</td><td>id</td><td>主键，自增</td></tr>
 *   <tr><td>noteId</td><td>note_id</td><td>被点赞的游记 ID，关联 note.id</td></tr>
 *   <tr><td>userId</td><td>user_id</td><td>点赞用户 ID，关联 sys_user.id</td></tr>
 *   <tr><td>createTime</td><td>create_time</td><td>点赞时间</td></tr>
 * </table>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("note_like")
public class NoteLike {
    /** 主键 ID，自增。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 被点赞的游记 ID，关联 note.id。 */
    private Long noteId;

    /** 点赞用户 ID，关联 sys_user.id。 */
    private Long userId;

    /** 点赞时间。 */
    private LocalDateTime createTime;
}
