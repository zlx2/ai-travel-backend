package com.sora.aitravel.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * AI 调用日志实体类。
 * <p>
 * 对应数据库表 {@code ai_call_log}，记录每次调用 AI 模型的详细日志，包括调用用户、
 * 所属会话、场景、模型名称、请求摘要（脱敏后）、响应摘要、调用是否成功、错误信息
 * 以及耗时等。失败调用必须落库以便排查问题。请求和响应仅记录必要摘要并对敏感信息
 * 进行脱敏处理，避免保存过大的完整 Prompt。
 * </p>
 *
 * <table border="1">
 *   <caption>字段与数据库列映射</caption>
 *   <tr><th>字段</th><th>数据库列</th><th>说明</th></tr>
 *   <tr><td>id</td><td>id</td><td>主键，自增</td></tr>
 *   <tr><td>userId</td><td>user_id</td><td>发起调用的用户 ID，关联 sys_user.id</td></tr>
 *   <tr><td>conversationId</td><td>conversation_id</td><td>所属 AI 对话 ID</td></tr>
 *   <tr><td>scene</td><td>scene</td><td>调用场景</td></tr>
 *   <tr><td>modelName</td><td>model_name</td><td>AI 模型名称，如 gpt-4o、deepseek-v3 等</td></tr>
 *   <tr><td>requestJson</td><td>request_json</td><td>请求摘要（脱敏后），避免保存完整 Prompt</td></tr>
 *   <tr><td>responseJson</td><td>response_json</td><td>响应摘要</td></tr>
 *   <tr><td>success</td><td>success</td><td>是否成功：0-失败，1-成功；失败调用必须落库</td></tr>
 *   <tr><td>errorMessage</td><td>error_message</td><td>错误信息，成功时为空</td></tr>
 *   <tr><td>durationMs</td><td>duration_ms</td><td>调用耗时，单位毫秒</td></tr>
 *   <tr><td>createTime</td><td>create_time</td><td>记录创建时间</td></tr>
 * </table>
 */
@Data
@TableName("ai_call_log")
public class AiCallLog {
    /** 主键 ID，自增。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发起调用的用户 ID，关联 sys_user.id。 */
    private Long userId;

    /** 所属 AI 对话的会话 ID，关联 ai_conversation.conversation_id。 */
    private String conversationId;

    /** 调用场景，如 TRIP_ANALYZE、TRIP_GENERATE 等。 */
    private String scene;

    /** AI 模型名称，如 gpt-4o、deepseek-v3、claude-3 等。 */
    private String modelName;

    /** 请求摘要（脱敏后），仅记录必要摘要，避免保存过大的完整 Prompt。 */
    private String requestJson;

    /** 响应摘要，记录 AI 返回的主要内容。 */
    private String responseJson;

    /** 调用是否成功：0=失败，1=成功；失败调用必须落库。 */
    private Integer success;

    /** 错误信息，成功时此字段为空。 */
    private String errorMessage;

    /** 调用耗时，单位毫秒。 */
    private Long durationMs;

    /** 记录创建时间。 */
    private LocalDateTime createTime;
}
