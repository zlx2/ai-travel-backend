package com.sora.aitravel.tools;

import com.sora.aitravel.dto.model.AmapApiResp;
import com.sora.aitravel.dto.model.route.Route;
import com.sora.aitravel.service.AmapApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AmapRouteTool {

    private final AmapApiService amapService;

    /** 驾车路线规划 */
    @Tool(description = "驾车路线规划，输入起点、终点经纬度，返回驾车距离、时长、分段道路、红绿灯、高速/收费路线方案")
    public AmapApiResp<Route> drivingRoute(
            @ToolParam(description = "起点经纬度，经度,纬度") String origin,
            @ToolParam(description = "终点经纬度，经度,纬度") String destination,
            @ToolParam(description = "路线策略：0推荐，1高速优先，2不走高速，3躲避拥堵，4少收费等", required = false)
                    Integer strategy,
            @ToolParam(description = "途经点坐标，多个用分号分隔", required = false) String waypoints,
            @ToolParam(description = "车牌，用于限行规避，如浙A12345", required = false) String plate,
            @ToolParam(description = "车辆类型 0普通车 1大车", required = false) Integer cartype) {
        return amapService.drivingRoute(origin, destination, strategy, waypoints, plate, cartype);
    }

    /** 步行路线规划 */
    @Tool(description = "步行路线规划，获取两点之间步行道路、步行距离、预估时间、过街天桥/地下通道信息")
    public AmapApiResp<Route> walkingRoute(
            @ToolParam(description = "起点经纬度，经度,纬度") String origin,
            @ToolParam(description = "终点经纬度，经度,纬度") String destination,
            @ToolParam(description = "返回备选路线条数，最大3条", required = false) Integer alternativeRoute) {
        return amapService.walkingRoute(origin, destination, alternativeRoute);
    }

    /** 普通骑行路线规划 */
    @Tool(description = "自行车骑行路线规划，获取非机动车道、骑行距离、预估时长路线方案")
    public AmapApiResp<Route> bicyclingRoute(
            @ToolParam(description = "起点经纬度，经度,纬度") String origin,
            @ToolParam(description = "终点经纬度，经度,纬度") String destination,
            @ToolParam(description = "备选路线数量", required = false) Integer alternativeRoute) {
        return amapService.bicyclingRoute(origin, destination, alternativeRoute);
    }

    /** 电动车路线规划 */
    @Tool(description = "电动车骑行路线规划，适配电动车限行、非机动车道路线方案")
    public AmapApiResp<Route> electrobikeRoute(
            @ToolParam(description = "起点经纬度，经度,纬度") String origin,
            @ToolParam(description = "终点经纬度，经度,纬度") String destination,
            @ToolParam(description = "备选路线数量", required = false) Integer alternativeRoute) {
        return amapService.electrobikeRoute(origin, destination, alternativeRoute);
    }

    /** 公交地铁换乘规划 */
    @Tool(description = "公交地铁一体化换乘规划，返回公交、地铁、步行组合换乘方案、票价、耗时、班次信息")
    public AmapApiResp<Route> transitRoute(
            @ToolParam(description = "起点经纬度，经度,纬度") String origin,
            @ToolParam(description = "终点经纬度，经度,纬度") String destination,
            @ToolParam(description = "起点城市名称或adcode") String city1,
            @ToolParam(description = "终点城市名称或adcode，跨城必填") String city2,
            @ToolParam(description = "换乘策略：0最快捷，1最少换乘，2最少步行，3不坐地铁等", required = false)
                    Integer strategy,
            @ToolParam(description = "返回换乘方案数量", required = false) Integer alternativeRoute,
            @ToolParam(description = "是否包含夜班车 0否 1是", required = false) Integer nightflag) {
        return amapService.transitRoute(
                origin, destination, city1, city2, strategy, alternativeRoute, nightflag);
    }
}
