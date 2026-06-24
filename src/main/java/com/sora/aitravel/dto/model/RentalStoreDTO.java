package com.sora.aitravel.dto.model;

import lombok.Data;

/**
 * 推荐租车服务点。
 *
 * <p>这是租车工作流的中间模型，可以继续作为库存、价格、异店还车、下单等节点的入参。字段来自地图 POI 解析和本地筛选规则，不表示平台自营门店，也不表示该服务点一定有车或可下单。
 */
@Data
public class RentalStoreDTO {
    /** 系统内临时服务点编码，当前由高德 POI ID 派生。 */
    private String storeCode;

    /** 面向用户展示的推荐点名称，例如“成都东站推荐取车点”。 */
    private String displayName;

    /** 数据来源标识，当前固定为 AMAP_DYNAMIC。 */
    private String source;

    /** 使用场景：PICKUP 或 RETURN。 */
    private String usage;

    /** 高德 POI 原始 ID，用于排查和后续地图能力衔接。 */
    private String amapPoiId;

    /** 高德 POI 原始商户名称，只能作为地图推荐信息展示，不能描述为平台直营网点。 */
    private String amapPoiName;

    /** 服务点地址。 */
    private String address;

    /** 城市名称。 */
    private String cityName;

    /** 行政区名称。 */
    private String adName;

    /** 行政区划代码。 */
    private String adCode;

    /** 经度。 */
    private String lng;

    /** 纬度。 */
    private String lat;

    /** 距离目标地点的直线/地图返回距离，单位米。 */
    private Integer distanceMeters;

    /** 高德 POI 类型编码。 */
    private String typeCode;

    /** 当日营业时间，可能为空。 */
    private String openTime;

    /** 联系电话，可能为空或包含多个号码。 */
    private String tel;
}
