package com.sora.aitravel.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 行程实体类。
 *
 * <p>对应数据库表 {@code trip}，存储用户创建的旅行行程信息。行程可以是 AI 智能生成、 用户手动创建或从模板复制而来。每个行程关联一个用户（userId）和一次 AI 对话
 * （conversationId），并包含出发地、目的地、天数、预算等关键参数，以及 AI 分析后 的旅行需求和完整行程计划（均以 JSON 格式存储）。
 * 该表通过逻辑删除标记（deleted）实现软删除。
 *
 * <table border="1">
 *   <caption>字段与数据库列映射</caption>
 *   <tr><th>字段</th><th>数据库列</th><th>说明</th></tr>
 *   <tr><td>id</td><td>id</td><td>主键，自增</td></tr>
 *   <tr><td>userId</td><td>user_id</td><td>所属用户 ID</td></tr>
 *   <tr><td>conversationId</td><td>conversation_id</td><td>关联的 AI 对话 ID</td></tr>
 *   <tr><td>title</td><td>title</td><td>行程标题</td></tr>
 *   <tr><td>departure</td><td>departure</td><td>出发地</td></tr>
 *   <tr><td>destination</td><td>destination</td><td>目的地</td></tr>
 *   <tr><td>days</td><td>days</td><td>行程天数</td></tr>
 *   <tr><td>budget</td><td>budget</td><td>预算金额（元）</td></tr>
 *   <tr><td>preferencesJson</td><td>preferences_json</td><td>用户旅行偏好的 JSON 文本</td></tr>
 *   <tr><td>requirementJson</td><td>requirement_json</td><td>AI 分析确认后的旅行需求 JSON</td></tr>
 *   <tr><td>tripPlanJson</td><td>trip_plan_json</td><td>完整行程计划 JSON</td></tr>
 *   <tr><td>summary</td><td>summary</td><td>行程摘要/简介</td></tr>
 *   <tr><td>coverUrl</td><td>cover_url</td><td>封面图片 URL</td></tr>
 *   <tr><td>source</td><td>source</td><td>来源：1-AI生成，2-手动创建，3-模板复制</td></tr>
 *   <tr><td>status</td><td>status</td><td>状态：1-正常，2-已删除</td></tr>
 *   <tr><td>createTime</td><td>create_time</td><td>记录创建时间</td></tr>
 *   <tr><td>updateTime</td><td>update_time</td><td>记录更新时间</td></tr>
 *   <tr><td>deleted</td><td>deleted</td><td>逻辑删除标记：0-未删除，1-已删除</td></tr>
 * </table>
 */
@Data
@TableName("trip")
public class Trip {
    /** 主键 ID，自增。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户 ID，关联 sys_user.id。 */
    private Long userId;

    /** 关联的 AI 对话 ID，用于追溯 AI 交互上下文。 */
    private String conversationId;

    /** 行程标题，由用户或 AI 生成。 */
    private String title;

    /** 出发地（城市名或具体地址）。 */
    private String departure;

    /** 目的地（城市名或景点名称）。 */
    private String destination;

    /** 行程天数。 */
    private Integer days;

    /** 预算金额，单位为元。 */
    private Integer budget;

    /** 用户旅行偏好数组的 JSON 文本，如美食、自然风光等。 */
    private String preferencesJson;

    /** AI 分析确认后的 TravelRequirement JSON，包含完整的出行需求。 */
    private String requirementJson;

    /** 完整 TripPlan JSON，包含每日详细安排；向 AI 追问时作为上下文传入。 */
    private String tripPlanJson;

    /** 行程摘要或短简介，用于列表展示。 */
    private String summary;

    /** 封面图片 URL。 */
    private String coverUrl;

    /** 行程来源：1=AI 生成，2=手动创建，3=模板复制；一期默认 1。 */
    private Integer source;

    /** 行程状态：1=正常，2=已删除。 */
    private Integer status;

    /** 记录创建时间。 */
    private LocalDateTime createTime;

    /** 记录更新时间。 */
    private LocalDateTime updateTime;

    /** 删除行程时必须与 status=2 同步设置。 */
    @TableLogic private Integer deleted;
}
