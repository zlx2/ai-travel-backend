package com.sora.aitravel.dto.model.route;

import lombok.Data;

/**
 * 方案所需时间及费用成本
 */
@Data
public class Cost {
    /**
     * 线路耗时，方案总耗时，包含等车时间，单位：秒
     * 所有规划都使用
     */
    private String duration;

    /**
     * 预估出租车费用
     * 公交规划中使用
     */
    private String taxi_fee;

    /**
     * 各换乘方案总花费
     * 公交规划中使用
     */
    private String transit_fee;

    /**
     * 预估打车费用
     * 步行规划中使用
     */
    private String taxi;

    /**
     * 此路线道路收费，单位：元，包括分段信息
     * 驾车规划中使用
     */
    private String tolls;

    /**
     * 收费路段里程，单位：米，包括分段信息此路线道路收费，单位：元，包括分段信息
     * 驾车规划中使用
     */
    private String toll_distance;

    /**
     * 主要收费道路
     * 驾车规划中使用
     */

    private String toll_road;

    /**
     * 方案中红绿灯个数，单位：个
     * 驾车规划中使用
     */
    private String traffic_lights;

}
