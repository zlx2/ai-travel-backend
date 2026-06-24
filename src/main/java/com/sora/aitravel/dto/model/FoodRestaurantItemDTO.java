package com.sora.aitravel.dto.model;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 饭店推荐条目 DTO。
 *
 * <p>一个对象代表一家饭店，最终会放在 FoodRecommendResponse 的 list 中返回给工作流。 字段主要来自高德 POI 和 business
 * 扩展信息；高德没有返回的数据保持为空，不手动编造评分、人均、营业时间。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FoodRestaurantItemDTO {
    /** 高德 POI 唯一 ID。 */
    private String amapPoiId;

    /** 饭店名称。 */
    private String name;

    /** 饭店地址。 */
    private String address;

    /** 所属城市。 */
    private String cityName;

    /** 所属区县。 */
    private String adName;

    /** 行政区划编码。 */
    private String adCode;

    /** 高德 POI 类型文本，例如“餐饮服务;中餐厅;火锅店”。 */
    private String type;

    /** 高德 POI 类型编码，餐饮大类通常以 05 开头。 */
    private String typeCode;

    /** 后端从高德标签中提取的简短美食类型，例如“火锅店”。 */
    private String foodType;

    /** 高德坐标字符串，格式为“经度,纬度”。 */
    private String location;

    /** 经度，便于后续工作流或前端直接使用。 */
    private BigDecimal longitude;

    /** 纬度，便于后续工作流或前端直接使用。 */
    private BigDecimal latitude;

    /** 距离中心点的米数，只有周边搜索通常会返回。 */
    private Integer distance;

    /** 后端加工后的距离文案，例如“距搜索地点约800米”。 */
    private String distanceText;

    /** 高德返回评分；没有返回则为空。 */
    private String rating;

    /** 高德返回人均消费；没有返回则为空。 */
    private String avgCost;

    /** 高德 business.tag 标签。 */
    private String tag;

    /** 高德 business.keytag 标签。 */
    private String keyTag;

    /** 高德 business.rectag 推荐标签。 */
    private String recTag;

    /** 高德返回营业时间；没有返回则为空。 */
    private String openTime;

    /** 高德返回商圈信息。 */
    private String businessArea;

    /** 高德返回联系电话。 */
    private String tel;

    /** AI 或模板生成的推荐理由，只基于真实字段生成。 */
    private String aiRecommendReason;

    /** 预留导航地址字段，首版为空，后续可补高德导航链接。 */
    private String navigationUrl;
}
