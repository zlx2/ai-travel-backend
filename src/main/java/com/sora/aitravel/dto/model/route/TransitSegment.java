package com.sora.aitravel.dto.model.route;

import lombok.Data;

/**
 * 公交分段
 */
@Data
public class TransitSegment {
    /**
     * 步行信息
     */
    private WalkInfo walking;

    /**
     * 公交信息
     */
    private BusInfo bus;

    /**
     * 出租车信息
     */
    private TaxiInfo taxi;

    /**
     * 地铁信息
     */
    private RailwayInfo railway;
}
