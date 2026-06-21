package com.sora.aitravel.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 目的地实体类。
 * <p>
 * 对应数据库表 {@code destination}，存储旅行目的地的基础信息，包括名称、所属省市、
 * 经纬度坐标、封面图片、描述标签等。目的地是行程和游记的核心关联对象，支持按热度
 * 排序和标签分类。该表通过逻辑删除标记（deleted）实现软删除。
 * </p>
 *
 * <table border="1">
 *   <caption>字段与数据库列映射</caption>
 *   <tr><th>字段</th><th>数据库列</th><th>说明</th></tr>
 *   <tr><td>id</td><td>id</td><td>主键，自增</td></tr>
 *   <tr><td>name</td><td>name</td><td>目的地名称</td></tr>
 *   <tr><td>province</td><td>province</td><td>所属省份</td></tr>
 *   <tr><td>city</td><td>city</td><td>所属城市</td></tr>
 *   <tr><td>longitude</td><td>longitude</td><td>经度坐标</td></tr>
 *   <tr><td>latitude</td><td>latitude</td><td>纬度坐标</td></tr>
 *   <tr><td>coverUrl</td><td>cover_url</td><td>封面图片 URL</td></tr>
 *   <tr><td>description</td><td>description</td><td>目的地简介/描述</td></tr>
 *   <tr><td>tagsJson</td><td>tags_json</td><td>目的地标签数组的 JSON 文本</td></tr>
 *   <tr><td>heat</td><td>heat</td><td>热度值，用于排序</td></tr>
 *   <tr><td>status</td><td>status</td><td>状态：0-禁用，1-启用</td></tr>
 *   <tr><td>createTime</td><td>create_time</td><td>记录创建时间</td></tr>
 *   <tr><td>updateTime</td><td>update_time</td><td>记录更新时间</td></tr>
 *   <tr><td>deleted</td><td>deleted</td><td>逻辑删除标记：0-未删除，1-已删除</td></tr>
 * </table>
 */
@Data
@TableName("destination")
public class Destination {
    /** 主键 ID，自增。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 目的地名称（如"大理"、"丽江"）。 */
    private String name;

    /** 所属省份。 */
    private String province;

    /** 所属城市。 */
    private String city;

    /** 经度坐标（WGS84 坐标系）。 */
    private BigDecimal longitude;

    /** 纬度坐标（WGS84 坐标系）。 */
    private BigDecimal latitude;

    /** 封面图片 URL。 */
    private String coverUrl;

    /** 目的地简介或详细描述。 */
    private String description;

    /** 目的地标签数组的 JSON 文本，如["自然风光","古镇"]。 */
    private String tagsJson;

    /** 热度值，数值越大越热门，用于排序展示。 */
    private Integer heat;

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
