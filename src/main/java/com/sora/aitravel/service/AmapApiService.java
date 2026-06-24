package com.sora.aitravel.service;

import com.sora.aitravel.dto.model.AmapApiResp;
import com.sora.aitravel.dto.model.geo.GeoCode;
import com.sora.aitravel.dto.model.geo.RegeoCode;
import com.sora.aitravel.dto.model.poi.Poi;
import com.sora.aitravel.dto.model.route.Route;
import com.sora.aitravel.dto.model.staticmap.StaticMapRequest;
import com.sora.aitravel.dto.model.staticmap.StaticMapResp;

import java.io.File;
import java.util.List;

public interface AmapApiService {
    /**
     * POI文本搜索
     *
     * @param keywords  地点关键词
     * @param types     POI类型
     * @param region    搜索区划
     * @param cityLimit 是否限制在区域内
     * @return POI搜索结果
     */
    AmapApiResp<List<Poi>> searchPoiText(String keywords, String types, String region, Boolean cityLimit);

    /**
     * POI文本搜索（简化版）
     */
    AmapApiResp<List<Poi>> searchPoiText(String keywords);

    /**
     * POI周边搜索
     *
     * @param location 中心点坐标（经度,纬度）
     * @param keywords 地点关键词
     * @param types    POI类型
     * @param radius   搜索半径（米）
     * @return POI搜索结果
     */
    AmapApiResp<List<Poi>> searchPoiAround(String location, String keywords, String types, Integer radius);

    /**
     * POI周边搜索（简化版）
     */
    AmapApiResp<List<Poi>> searchPoiAround(String location, Integer radius);

    /**
     * 地名转经纬度（地理编码）
     *
     * @param address 结构化地址信息
     * @param city    指定查询的城市
     * @return 地理编码结果
     */
    AmapApiResp<List<GeoCode>> geoCode(String address, String city);

    /**
     * 地名转经纬度（简化版）
     */
    AmapApiResp<List<GeoCode>> geoCode(String address);

    /**
     * 经纬度转地名（逆地理编码）
     *
     * @param location   经纬度坐标
     * @param radius     搜索半径
     * @param extensions 返回结果控制（base/all）
     * @return 逆地理编码结果
     */
    AmapApiResp<RegeoCode> reGeoCode(String location, Integer radius, String extensions);

    /**
     * 经纬度转地名（简化版）
     */
    AmapApiResp<RegeoCode> reGeoCode(String location);

    /**
     * 驾车路径规划
     *
     * @param origin      起点经纬度
     * @param destination 目的地经纬度
     * @param strategy    算路策略
     * @param waypoints   途经点
     * @param plate       车牌号码
     * @param cartype     车辆类型
     * @return 路线规划结果
     */
    AmapApiResp<Route> drivingRoute(String origin, String destination, Integer strategy, String waypoints, String plate, Integer cartype);

    /**
     * 驾车路径规划（简化版）
     */
    AmapApiResp<Route> drivingRoute(String origin, String destination);

    /**
     * 步行路径规划
     *
     * @param origin           起点经纬度
     * @param destination      目的地经纬度
     * @param alternativeRoute 返回路线条数
     * @return 路线规划结果
     */
    AmapApiResp<Route> walkingRoute(String origin, String destination, Integer alternativeRoute);

    /**
     * 步行路径规划（简化版）
     */
    AmapApiResp<Route> walkingRoute(String origin, String destination);

    /**
     * 骑行路径规划
     */
    AmapApiResp<Route> bicyclingRoute(String origin, String destination, Integer alternativeRoute);

    /**
     * 骑行路径规划（简化版）
     */
    AmapApiResp<Route> bicyclingRoute(String origin, String destination);

    /**
     * 电动车路径规划
     */
    AmapApiResp<Route> electrobikeRoute(String origin, String destination, Integer alternativeRoute);

    /**
     * 公交路径规划
     *
     * @param origin           起点经纬度
     * @param destination      目的地经纬度
     * @param city1            起点所在城市
     * @param city2            目的地所在城市
     * @param strategy         换乘策略
     * @param alternativeRoute 返回方案条数
     * @param nightflag        是否考虑夜班车
     * @return 路线规划结果
     */
    AmapApiResp<Route> transitRoute(String origin, String destination, String city1, String city2, Integer strategy, Integer alternativeRoute, Integer nightflag);

    /**
     * 公交路径规划（简化版）
     */
    AmapApiResp<Route> transitRoute(String origin, String destination, String city1, String city2);

    /**
     * 获取静态地图
     */
    StaticMapResp staticMap(StaticMapRequest request);

    /**
     * 保存静态地图图片到指定路径字符串
     *
     * @param resp     静态地图返回结果
     * @param savePath 保存路径，如 ./map/map.png
     * @return File对象
     */
    File saveStaticMapImage(StaticMapResp resp, String savePath);

    /**
     * 保存静态地图图片到目标File
     *
     * @param resp       静态地图返回结果
     * @param targetFile 目标文件
     * @return 写入后的文件
     */
    File saveStaticMapImage(StaticMapResp resp, File targetFile);
}