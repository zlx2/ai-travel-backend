package com.sora.aitravel.dto.model.route;

import java.util.List;
import lombok.Data;

/** 路线方案（驾车/步行/骑行/电动车） */
@Data
public class Path {
    /** 方案距离，单位：米 */
    private String distance;

    /** 预计时间，单位：秒 */
    private String duration;

    /** 高德 2.0 返回的耗时、收费、红绿灯等成本信息。 */
    private Cost cost;

    /** 限行标识：0可规避，1无法规避 */
    private String restriction;

    /** 路线分段 */
    private List<Step> steps;
}
