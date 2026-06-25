package com.sora.aitravel.dto.model.route;

import java.util.List;

import lombok.Data;

/**
 * 步行信息
 */
@Data
public class WalkInfo {
    /**
     * 目的地经纬度，格式：经度,纬度
     */
    private String destination;

    /**
     * 距离，单位：米
     */
    private String distance;

    /**
     * 起点经纬度，格式：经度,纬度
     */
    private String origin;

    /**
     * 方案所需时间及费用成本
     */
    private Cost cost;
}
