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
     * 方案所需时间及费用成本
     * 公交规划中使用
     */
    private Cost cost;

    /**
     * 路线方案列表
     */
    private List<Path> paths;

    /**
     * 公交方案列表
     */
    private List<Transit> transits;
}

