package com.sora.aitravel.dto.model.route;

import lombok.Data;

/** 火车信息 */
@Data
public class RailwayInfo {
    /** 线路 id 编号 */
    private String id;

    /** 该线路车段耗时 */
    private String time;

    /** 线路名称 */
    private String name;

    /** 线路车次号 */
    private String trip;

    /** 该 item 换乘段的行车总距离 */
    private String distance;

    /** 线路车次类型 */
    private String type;
}
