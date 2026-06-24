package com.sora.aitravel.dto.model.route;

import lombok.Data;

import java.util.List;

/**
 * 路线信息
 */
@Data
public class Route {
    /**
     * 起点经纬度
     */
    private String origin;

    /**
     * 终点经纬度
     */
    private String destination;

    /**
     * 预计出租车费用，单位：元
     */
    private String taxiCost;

    /**
     * 路线方案列表
     */
    private List<Path> paths;

    /**
     * 公交方案列表
     */
    private List<Transit> transits;
}

