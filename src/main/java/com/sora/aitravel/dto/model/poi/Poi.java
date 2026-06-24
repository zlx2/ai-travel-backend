package com.sora.aitravel.dto.model.poi;

import lombok.Data;

/**
 * POI信息
 */
@Data
public class Poi {
    /**
     * POI名称
     */
    private String name;

    /**
     * POI唯一标识
     */
    private String id;

    /**
     * 父POI的ID
     */
    private String parent;

    /**
     * 离中心点距离，单位米
     */
    private String distance;

    /**
     * POI经纬度
     */
    private String location;

    /**
     * POI所属类型
     */
    private String type;

    /**
     * POI分类编码
     */
    private String typecode;

    /**
     * POI所属省份
     */
    private String pname;

    /**
     * POI所属城市
     */
    private String cityname;

    /**
     * POI所属区县
     */
    private String adname;

    /**
     * POI详细地址
     */
    private String address;

    /**
     * POI所属省份编码
     */
    private String pcode;

    /**
     * POI所属区域编码
     */
    private String adcode;

    /**
     * POI所属城市编码
     */
    private String citycode;
}
