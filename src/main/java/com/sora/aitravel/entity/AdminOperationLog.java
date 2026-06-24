package com.sora.aitravel.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 管理员操作日志实体类。
 *
 * <p>对应数据库表 {@code admin_operation_log}，记录管理员在后端管理系统中的操作行为， 用于审计和追责。每条日志记录操作的管理员 ID、操作类型、操作目标类型、目标
 * ID、 操作内容和请求 IP 地址，以及操作发生时间。
 *
 * <table border="1">
 *   <caption>字段与数据库列映射</caption>
 *   <tr><th>字段</th><th>数据库列</th><th>说明</th></tr>
 *   <tr><td>id</td><td>id</td><td>主键，自增</td></tr>
 *   <tr><td>adminId</td><td>admin_id</td><td>操作管理员 ID，关联 sys_user.id</td></tr>
 *   <tr><td>operation</td><td>operation</td><td>操作类型，如 DELETE_NOTE、DISABLE_USER 等</td></tr>
 *   <tr><td>targetType</td><td>target_type</td><td>操作目标类型，如 note、user、trip</td></tr>
 *   <tr><td>targetId</td><td>target_id</td><td>操作目标的主键 ID</td></tr>
 *   <tr><td>content</td><td>content</td><td>操作详细内容或备注</td></tr>
 *   <tr><td>ip</td><td>ip</td><td>管理员操作时的请求 IP 地址</td></tr>
 *   <tr><td>createTime</td><td>create_time</td><td>操作发生时间</td></tr>
 * </table>
 */
@Data
@TableName("admin_operation_log")
public class AdminOperationLog {
    /** 主键 ID，自增。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作管理员 ID，关联 sys_user.id（role=2 的用户）。 */
    private Long adminId;

    /** 操作类型，如 DELETE_NOTE（删除游记）、DISABLE_USER（禁用用户）等。 */
    private String operation;

    /** 操作目标类型，如 note（游记）、user（用户）、trip（行程）。 */
    private String targetType;

    /** 操作目标的主键 ID。 */
    private Long targetId;

    /** 操作详细内容或变更备注。 */
    private String content;

    /** 管理员发起操作时的请求 IP 地址。 */
    private String ip;

    /** 操作发生时间。 */
    private LocalDateTime createTime;
}
