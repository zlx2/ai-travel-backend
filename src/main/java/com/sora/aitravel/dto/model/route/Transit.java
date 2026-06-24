package com.sora.aitravel.dto.model.route;

import java.util.List;
import lombok.Data;

/** 公交方案 */
@Data
public class Transit {
    /** 总距离，单位：米 */
    private String distance;

    /** 预计时间，单位：秒 */
    private String duration;

    /** 夜班车标识：0非夜班车，1夜班车 */
    private String nightflag;

    /** 路线分段 */
    private List<TransitSegment> segments;

    /**
     * 方案所需时间及费用成本
     */
    private Cost cost;
}
