package com.sora.aitravel.dto.model.route;

import lombok.Data;

/**
 * 出租车信息
 */
@Data
public class TaxiInfo {
    /**
     * 打车预计花费金额
     */
    private String price;

    /**
     * 打车预计花费时间
     */
    private String drivetime;

    /**
     * 打车距离
     */
    private String distance;
}
