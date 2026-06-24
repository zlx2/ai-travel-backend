package com.sora.aitravel.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * AI 对话会话实体类。
 *
 * <p>对应数据库表 {@code ai_conversation}，记录用户与 AI 助手之间的一次完整对话会话。 每个会话包含唯一的会话 ID（conversationId），关联的用户
 * ID，所属业务场景 （如行程分析、行程生成、AI 聊天），以及用于多轮追问的上下文快照。 上下文快照（contextJson）同时存储在 Redis（热数据）和 MySQL（持久化恢复）中。
 * 会话状态包括正常、已结束和已过期三种。
 *
 * <table border="1">
 *   <caption>字段与数据库列映射</caption>
 *   <tr><th>字段</th><th>数据库列</th><th>说明</th></tr>
 *   <tr><td>id</td><td>id</td><td>主键，自增</td></tr>
 *   <tr><td>conversationId</td><td>conversation_id</td><td>业务层唯一对话 ID，UUID 字符串</td></tr>
 *   <tr><td>userId</td><td>user_id</td><td>所属用户 ID，关联 sys_user.id</td></tr>
 *   <tr><td>scene</td><td>scene</td><td>对话场景：TRIP_ANALYZE、TRIP_GENERATE 或 AI_CHAT</td></tr>
 *   <tr><td>status</td><td>status</td><td>会话状态：1-正常，2-已结束，3-已过期</td></tr>
 *   <tr><td>contextJson</td><td>context_json</td><td>多轮追问所需的上下文快照 JSON</td></tr>
 *   <tr><td>createTime</td><td>create_time</td><td>会话创建时间</td></tr>
 *   <tr><td>updateTime</td><td>update_time</td><td>会话更新时间</td></tr>
 * </table>
 */
@Data
@TableName("ai_conversation")
public class AiConversation {
    /** 主键 ID，自增。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务层唯一对话标识，UUID 格式字符串。 */
    private String conversationId;

    /** 所属用户 ID，关联 sys_user.id。 */
    private Long userId;

    /** 对话场景：TRIP_ANALYZE（行程分析）、TRIP_GENERATE（行程生成）或 AI_CHAT（AI 聊天）。 */
    private String scene;

    /** 会话状态：1=正常，2=已结束，3=已过期。 */
    private Integer status;

    /** 多轮追问所需的上下文快照；Redis 为热数据，MySQL 用于恢复和追踪。 */
    private String contextJson;

    /** 会话创建时间。 */
    private LocalDateTime createTime;

    /** 会话更新时间。 */
    private LocalDateTime updateTime;
}
