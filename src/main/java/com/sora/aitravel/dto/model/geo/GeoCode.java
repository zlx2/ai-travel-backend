package com.sora.aitravel.dto.model.geo;

import lombok.Data;

/** 地理编码结果 */
@Data
public class GeoCode {
    /** 格式化地址 */
    private String formattedAddress;

    /** 国家 */
    private String country;

    /** 省份 */
    private String province;

    /** 城市 */
    private String city;

    /** 城市编码 */
    private String citycode;

    /** 区县 */
    private String district;

    /** 区域编码 */
    private String adcode;

    /** 街道 */
    private String street;

    /** 门牌号 */
    private String number;

    /** 经纬度坐标 */
    private String location;

    /** 匹配级别 */
    private String level;
}
