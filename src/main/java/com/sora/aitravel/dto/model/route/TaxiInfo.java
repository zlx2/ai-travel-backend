package com.sora.aitravel.dto.model.route;

import lombok.Data;

/**
 * 出租车信息
 */
@Data
public class TaxiInfo {
    /**
     * 打车预计花费金额
     * 单位：元
     **/
    private String price;

    /**
     * 打车预计花费时间
     * 单位：秒
     **/
    private String drivetime;

    /**
     * 打车距离
     * 单位：米
     **/
    private String distance;

    /**
     * 线路点集合，通过 show_fields 控制返回与否
     **/
    private String polyline;

    /**
     * 打车起点经纬度
     **/
    private String startpoint;

    /**
     * 打车起点名称
     **/
    private String startname;

    /**
     * 打车终点经纬度
     **/
    private String endpoint;

    /**
     * 打车终点名称
     **/
    private String endname;
}
