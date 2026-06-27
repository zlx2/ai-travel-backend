package com.sora.aitravel.dto.model.route;

import lombok.Data;

/** 公交站点信息 */
@Data
public class BusLine {
    /** 公交路线名称 格式如：445路(南十里居--地铁望京西站) */
    private String name;

    /** 公交路线 id 格式如：445路 */
    private String id;

    /** 公交类型 格式如：地铁线路 */
    private String type;

    /** 公交行驶距离 单位：米 */
    private Double distance;

    /** 公交预计行驶时间 单位：秒 */
    private Double duration;

    /** 此路段坐标集 格式为坐标串，如：116.481247,39.990704;116.481270,39.990726 */
    private String polyline;

    /** 此段途经公交站数 */
    private Integer via_num;
}
