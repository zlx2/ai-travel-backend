package com.sora.aitravel.dto.model.route;

import lombok.Data;

/** 路线分段 */
@Data
public class Step {
    /** 行驶指示 */
    private String instruction;

    /** 进入道路方向 */
    private String orientation;

    /** 分段道路名称 */
    private String roadName;

    /** 分段距离，单位：米 */
    private String stepDistance;

    /** 方案所需时间及费用成本 */
    private Cost cost;

    /** 分路段坐标点串，两点间用“;”分隔 */
    private String polyline;
}
