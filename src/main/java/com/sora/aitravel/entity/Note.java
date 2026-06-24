package com.sora.aitravel.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 游记（攻略）实体类。
 *
 * <p>对应数据库表 {@code note}，存储用户发布的旅行游记/攻略内容，包含标题、封面图片、 目的地、摘要、Markdown 正文，以及浏览、点赞、收藏、评论等互动计数。
 * 游记分三种状态：草稿（未发布）、已发布、已删除。该表通过逻辑删除标记（deleted） 实现软删除，删除时需同步将 status 置为 2。
 *
 * <table border="1">
 *   <caption>字段与数据库列映射</caption>
 *   <tr><th>字段</th><th>数据库列</th><th>说明</th></tr>
 *   <tr><td>id</td><td>id</td><td>主键，自增</td></tr>
 *   <tr><td>userId</td><td>user_id</td><td>作者用户 ID</td></tr>
 *   <tr><td>title</td><td>title</td><td>游记标题</td></tr>
 *   <tr><td>coverUrl</td><td>cover_url</td><td>封面图片 URL</td></tr>
 *   <tr><td>destination</td><td>destination</td><td>游记关联的目的地</td></tr>
 *   <tr><td>summary</td><td>summary</td><td>游记摘要</td></tr>
 *   <tr><td>content</td><td>content</td><td>Markdown 正文</td></tr>
 *   <tr><td>status</td><td>status</td><td>状态：0-草稿，1-已发布，2-已删除</td></tr>
 *   <tr><td>viewCount</td><td>view_count</td><td>浏览数</td></tr>
 *   <tr><td>likeCount</td><td>like_count</td><td>点赞数</td></tr>
 *   <tr><td>favoriteCount</td><td>favorite_count</td><td>收藏数</td></tr>
 *   <tr><td>commentCount</td><td>comment_count</td><td>评论数</td></tr>
 *   <tr><td>createTime</td><td>create_time</td><td>记录创建时间</td></tr>
 *   <tr><td>updateTime</td><td>update_time</td><td>记录更新时间</td></tr>
 *   <tr><td>deleted</td><td>deleted</td><td>逻辑删除标记：0-未删除，1-已删除</td></tr>
 * </table>
 */
@Data
@TableName("note")
public class Note {
    /** 主键 ID，自增。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 作者用户 ID，关联 sys_user.id。 */
    private Long userId;

    /** 游记标题。 */
    private String title;

    /** 封面图片 URL。 */
    private String coverUrl;

    /** 游记关联的目的地（城市或景点名称）。 */
    private String destination;

    /** 游记摘要或简短介绍，用于列表/卡片展示。 */
    private String summary;

    /** Markdown 正文；一期不支持正文图片上传。 */
    private String content;

    /** 发布状态：0=草稿，1=已发布，2=已删除；一期不含审核状态。 */
    private Integer status;

    /** 浏览量计数。 */
    private Integer viewCount;

    /** 点赞数计数。 */
    private Integer likeCount;

    /** 收藏数计数。 */
    private Integer favoriteCount;

    /** 评论数计数。 */
    private Integer commentCount;

    /** 记录创建时间。 */
    private LocalDateTime createTime;

    /** 记录更新时间。 */
    private LocalDateTime updateTime;

    /** 删除游记时必须与 status=2 同步设置。 */
    @TableLogic private Integer deleted;
}
