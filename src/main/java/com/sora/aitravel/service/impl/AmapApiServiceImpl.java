package com.sora.aitravel.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.sora.aitravel.dto.model.AmapApiResp;
import com.sora.aitravel.dto.model.geo.GeoCode;
import com.sora.aitravel.dto.model.geo.RegeoCode;
import com.sora.aitravel.dto.model.poi.Poi;
import com.sora.aitravel.dto.model.route.Route;
import com.sora.aitravel.dto.model.staticmap.StaticMapRequest;
import com.sora.aitravel.dto.model.staticmap.StaticMapResp;
import com.sora.aitravel.service.AmapApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AmapApiServiceImpl implements AmapApiService {
    @Value("${app.amap.base-url}")
    private static String BASE_URL = "https://restapi.amap.com";
    @Value("${app.amap.api-key:}")
    private String apiKey = System.getenv("AMAP_API_KEY");
    @Value("${app.amap.timeout:10000}")
    private Duration timeout = Duration.ofSeconds(10);


    // POI搜索
    private static final String POI_TEXT_URL = BASE_URL + "/v5/place/text";
    private static final String POI_AROUND_URL = BASE_URL + "/v5/place/around";

    // 地理编码
    private static final String GEO_URL = BASE_URL + "/v3/geocode/geo";
    private static final String REGEO_URL = BASE_URL + "/v3/geocode/regeo";

    // 路径规划
    private static final String DRIVING_URL = BASE_URL + "/v5/direction/driving";
    private static final String WALKING_URL = BASE_URL + "/v5/direction/walking";
    private static final String BICYCLING_URL = BASE_URL + "/v5/direction/bicycling";
    private static final String ELECTROBIKE_URL = BASE_URL + "/v5/direction/electrobike";
    private static final String TRANSIT_URL = BASE_URL + "/v5/direction/transit/integrated";

    // 静态地图API地址
    private static final String STATIC_MAP_URL = BASE_URL + "/v3/staticmap";


    // ==================== POI搜索 ====================

    /**
     * POI文本搜索
     *
     * @param keywords  地点关键词
     * @param types     POI类型
     * @param region    搜索区划
     * @param cityLimit 是否限制在区域内
     * @return POI搜索结果
     */
    public AmapApiResp<List<Poi>> searchPoiText(String keywords, String types,
                                                String region, Boolean cityLimit) {
        Map<String, Object> params = new HashMap<>();
        params.put("key", apiKey);

        if (StrUtil.isNotBlank(keywords)) {
            params.put("keywords", keywords);
        }
        if (StrUtil.isNotBlank(types)) {
            params.put("types", types);
        }
        if (StrUtil.isNotBlank(region)) {
            params.put("region", region);
        }
        if (cityLimit != null) {
            params.put("city_limit", cityLimit);
        }

        // 验证：keywords和types至少有一个
        if (StrUtil.isBlank(keywords) && StrUtil.isBlank(types)) {
            throw new IllegalArgumentException("keywords和types至少需要提供一个");
        }

        String response = executeGet(POI_TEXT_URL, params);
        return parsePoiResponse(response);
    }

    /**
     * POI文本搜索（简化版）
     */
    public AmapApiResp<List<Poi>> searchPoiText(String keywords) {
        return searchPoiText(keywords, null, null, null);
    }

    /**
     * POI周边搜索
     *
     * @param location 中心点坐标（经度,纬度）
     * @param keywords 地点关键词
     * @param types    POI类型
     * @param radius   搜索半径（米）
     * @return POI搜索结果
     */
    public AmapApiResp<List<Poi>> searchPoiAround(String location, String keywords,
                                                  String types, Integer radius) {
        Map<String, Object> params = new HashMap<>();
        params.put("key", apiKey);
        params.put("location", location);

        if (StrUtil.isNotBlank(keywords)) {
            params.put("keywords", keywords);
        }
        if (StrUtil.isNotBlank(types)) {
            params.put("types", types);
        }
        if (radius != null && radius > 0) {
            params.put("radius", radius);
        }

        String response = executeGet(POI_AROUND_URL, params);
        return parsePoiResponse(response);
    }

    /**
     * POI周边搜索（简化版）
     */
    public AmapApiResp<List<Poi>> searchPoiAround(String location, Integer radius) {
        return searchPoiAround(location, null, null, radius);
    }

    // ==================== 地理编码 ====================

    /**
     * 地名转经纬度（地理编码）
     *
     * @param address 结构化地址信息
     * @param city    指定查询的城市
     * @return 地理编码结果
     */
    public AmapApiResp<List<GeoCode>> geoCode(String address, String city) {
        Map<String, Object> params = new HashMap<>();
        params.put("key", apiKey);
        params.put("address", address);

        if (StrUtil.isNotBlank(city)) {
            params.put("city", city);
        }

        String response = executeGet(GEO_URL, params);
        return parseGeoCodeResponse(response);
    }

    /**
     * 地名转经纬度（简化版）
     */
    public AmapApiResp<List<GeoCode>> geoCode(String address) {
        return geoCode(address, null);
    }

    /**
     * 经纬度转地名（逆地理编码）
     *
     * @param location   经纬度坐标
     * @param radius     搜索半径
     * @param extensions 返回结果控制（base/all）
     * @return 逆地理编码结果
     */
    public AmapApiResp<RegeoCode> reGeoCode(String location, Integer radius, String extensions) {
        Map<String, Object> params = new HashMap<>();
        params.put("key", apiKey);
        params.put("location", location);

        if (radius != null && radius > 0) {
            params.put("radius", radius);
        }
        if (StrUtil.isNotBlank(extensions)) {
            params.put("extensions", extensions);
        }

        String response = executeGet(REGEO_URL, params);
        return parseRegeoCodeResponse(response);
    }

    /**
     * 经纬度转地名（简化版）
     */
    public AmapApiResp<RegeoCode> reGeoCode(String location) {
        return reGeoCode(location, null, "base");
    }

    // ==================== 路径规划 ====================

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
    public AmapApiResp<Route> drivingRoute(String origin, String destination,
                                           Integer strategy, String waypoints,
                                           String plate, Integer cartype) {
        Map<String, Object> params = buildRouteParams(origin, destination);

        if (strategy != null) {
            params.put("strategy", strategy);
        }
        if (StrUtil.isNotBlank(waypoints)) {
            params.put("waypoints", waypoints);
        }
        if (StrUtil.isNotBlank(plate)) {
            params.put("plate", plate);
        }
        if (cartype != null) {
            params.put("cartype", cartype);
        }

        String response = executeGet(DRIVING_URL, params);
        return parseRouteResponse(response);
    }

    /**
     * 驾车路径规划（简化版）
     */
    public AmapApiResp<Route> drivingRoute(String origin, String destination) {
        return drivingRoute(origin, destination, null, null, null, null);
    }

    /**
     * 步行路径规划
     *
     * @param origin           起点经纬度
     * @param destination      目的地经纬度
     * @param alternativeRoute 返回路线条数
     * @return 路线规划结果
     */
    public AmapApiResp<Route> walkingRoute(String origin, String destination,
                                           Integer alternativeRoute) {
        Map<String, Object> params = buildRouteParams(origin, destination);

        if (alternativeRoute != null) {
            params.put("alternative_route", alternativeRoute);
        }

        String response = executeGet(WALKING_URL, params);
        return parseRouteResponse(response);
    }

    /**
     * 步行路径规划（简化版）
     */
    public AmapApiResp<Route> walkingRoute(String origin, String destination) {
        return walkingRoute(origin, destination, null);
    }

    /**
     * 骑行路径规划
     */
    public AmapApiResp<Route> bicyclingRoute(String origin, String destination,
                                             Integer alternativeRoute) {
        Map<String, Object> params = buildRouteParams(origin, destination);

        if (alternativeRoute != null) {
            params.put("alternative_route", alternativeRoute);
        }

        String response = executeGet(BICYCLING_URL, params);
        return parseRouteResponse(response);
    }

    /**
     * 骑行路径规划（简化版）
     */
    public AmapApiResp<Route> bicyclingRoute(String origin, String destination) {
        return bicyclingRoute(origin, destination, null);
    }

    /**
     * 电动车路径规划
     */
    public AmapApiResp<Route> electrobikeRoute(String origin, String destination,
                                               Integer alternativeRoute) {
        Map<String, Object> params = buildRouteParams(origin, destination);

        if (alternativeRoute != null) {
            params.put("alternative_route", alternativeRoute);
        }

        String response = executeGet(ELECTROBIKE_URL, params);
        return parseRouteResponse(response);
    }

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
    public AmapApiResp<Route> transitRoute(String origin, String destination,
                                           String city1, String city2,
                                           Integer strategy, Integer alternativeRoute,
                                           Integer nightflag) {
        Map<String, Object> params = buildRouteParams(origin, destination);
        params.put("city1", city1);
        params.put("city2", city2);

        if (strategy != null) {
            params.put("strategy", strategy);
        }
        if (alternativeRoute != null) {
            params.put("AlternativeRoute", alternativeRoute);
        }
        if (nightflag != null) {
            params.put("nightflag", nightflag);
        }

        String response = executeGet(TRANSIT_URL, params);
        return parseRouteResponse(response);
    }

    /**
     * 公交路径规划（简化版）
     */
    public AmapApiResp<Route> transitRoute(String origin, String destination,
                                           String city1, String city2) {
        return transitRoute(origin, destination, city1, city2, null, null, null);
    }

    // ==================== 私有方法 ====================

    /**
     * 构建通用路径规划参数
     */
    private Map<String, Object> buildRouteParams(String origin, String destination) {
        Map<String, Object> params = new HashMap<>();
        params.put("key", apiKey);
        params.put("origin", origin);
        params.put("destination", destination);
        return params;
    }

    /**
     * 发送GET请求
     */
    private String executeGet(String url, Map<String, Object> params) {
        try {
            // 使用Hutool的HttpRequest
            HttpRequest request = HttpUtil.createGet(url)
                    .timeout(timeout.toMillisPart())
                    .form(MapUtil.toCamelCaseMap(params));

            try (HttpResponse response = request.execute()) {
                String body = response.body();
                log.debug("Request URL: {}", request.getUrl());
                log.debug("Response: {}", body);
                return body;
            }
        } catch (Exception e) {
            log.error("请求高德API失败: {}", e.getMessage(), e);
            throw new RuntimeException("请求高德API失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析POI搜索响应
     */
    private AmapApiResp<List<Poi>> parsePoiResponse(String json) {
        AmapApiResp<List<Poi>> result = new AmapApiResp<>();
        result.setRawJson(json);

        try {
            JSONObject obj = JSONUtil.parseObj(json);
            result.setStatus(obj.getStr("status"));
            result.setInfo(obj.getStr("info"));
            result.setInfocode(obj.getStr("infocode"));
            result.setCount(obj.getStr("count"));

            if (result.isSuccess()) {
                JSONArray poisArray = obj.getJSONArray("pois");
                if (poisArray != null) {
                    List<Poi> pois = new ArrayList<>();
                    for (Object poiObj : poisArray) {
                        JSONObject poiJson = (JSONObject) poiObj;
                        Poi poi = JSONUtil.toBean(poiJson, Poi.class);
                        pois.add(poi);
                    }
                    result.setData(pois);
                }
            }
        } catch (Exception e) {
            log.error("解析POI响应失败: {}", e.getMessage(), e);
            result.setStatus("0");
            result.setInfo("解析响应失败");
        }

        return result;
    }

    /**
     * 解析地理编码响应
     */
    private AmapApiResp<List<GeoCode>> parseGeoCodeResponse(String json) {
        AmapApiResp<List<GeoCode>> result = new AmapApiResp<>();
        result.setRawJson(json);

        try {
            JSONObject obj = JSONUtil.parseObj(json);
            result.setStatus(obj.getStr("status"));
            result.setInfo(obj.getStr("info"));
            result.setInfocode(obj.getStr("infocode"));
            result.setCount(obj.getStr("count"));

            if (result.isSuccess()) {
                JSONArray geocodesArray = obj.getJSONArray("geocodes");
                if (geocodesArray != null) {
                    List<GeoCode> geocodes = new ArrayList<>();
                    for (Object geoObj : geocodesArray) {
                        JSONObject geoJson = (JSONObject) geoObj;
                        GeoCode geoCode = JSONUtil.toBean(geoJson, GeoCode.class);
                        geocodes.add(geoCode);
                    }
                    result.setData(geocodes);
                }
            }
        } catch (Exception e) {
            log.error("解析地理编码响应失败: {}", e.getMessage(), e);
            result.setStatus("0");
            result.setInfo("解析响应失败");
        }

        return result;
    }

    /**
     * 解析逆地理编码响应
     */
    private AmapApiResp<RegeoCode> parseRegeoCodeResponse(String json) {
        AmapApiResp<RegeoCode> result = new AmapApiResp<>();
        result.setRawJson(json);

        try {
            JSONObject obj = JSONUtil.parseObj(json);
            result.setStatus(obj.getStr("status"));
            result.setInfo(obj.getStr("info"));
            result.setInfocode(obj.getStr("infocode"));

            if (result.isSuccess()) {
                JSONObject regeoObj = obj.getJSONObject("regeocode");
                if (regeoObj != null) {
                    RegeoCode regeoCode = JSONUtil.toBean(regeoObj, RegeoCode.class);
                    result.setData(regeoCode);
                }
            }
        } catch (Exception e) {
            log.error("解析逆地理编码响应失败: {}", e.getMessage(), e);
            result.setStatus("0");
            result.setInfo("解析响应失败");
        }

        return result;
    }

    /**
     * 解析路径规划响应
     */
    private AmapApiResp<Route> parseRouteResponse(String json) {
        AmapApiResp<Route> result = new AmapApiResp<>();
        result.setRawJson(json);

        try {
            JSONObject obj = JSONUtil.parseObj(json);
            result.setStatus(obj.getStr("status"));
            result.setInfo(obj.getStr("info"));
            result.setInfocode(obj.getStr("infocode"));
            result.setCount(obj.getStr("count"));

            if (result.isSuccess()) {
                JSONObject routeObj = obj.getJSONObject("route");
                if (routeObj != null) {
                    Route route = JSONUtil.toBean(routeObj, Route.class);
                    result.setData(route);
                }
            }
        } catch (Exception e) {
            log.error("解析路径规划响应失败: {}", e.getMessage(), e);
            result.setStatus("0");
            result.setInfo("解析响应失败");
        }

        return result;
    }

    // ===================== 静态地图请求接口 =====================
    public StaticMapResp staticMap(StaticMapRequest request) {
        // 填充key
        request.setKey(apiKey);
        // 参数校验
        validateStaticMapParam(request);

        HttpRequest httpRequest = HttpRequest.get(STATIC_MAP_URL)
                .timeout(timeout.toMillisPart());

        // 填充非空参数
        if (StrUtil.isNotBlank(request.getKey())) httpRequest.form("key", request.getKey());
        if (StrUtil.isNotBlank(request.getLocation())) httpRequest.form("location", request.getLocation());
        if (request.getZoom() != null) httpRequest.form("zoom", request.getZoom());
        if (StrUtil.isNotBlank(request.getSize())) httpRequest.form("size", request.getSize());
        if (request.getScale() != null) httpRequest.form("scale", request.getScale());
        if (StrUtil.isNotBlank(request.getMarkers())) httpRequest.form("markers", request.getMarkers());
        if (StrUtil.isNotBlank(request.getLabels())) httpRequest.form("labels", request.getLabels());
        if (StrUtil.isNotBlank(request.getPaths())) httpRequest.form("paths", request.getPaths());
        if (request.getTraffic() != null) httpRequest.form("traffic", request.getTraffic());

        String fullUrl = httpRequest.getUrl();
        try {
            HttpResponse response = httpRequest.execute();
            StaticMapResp resp = StaticMapResp.builder()
                    .requestUrl(fullUrl)
                    .contentType(response.header("Content-Type"))
                    .build();

            String contentType = resp.getContentType();
            if (contentType != null && contentType.contains("image")) {
                resp.setImageBytes(response.bodyBytes());
            } else {
                resp.setRawText(response.body());
            }
            return resp;
        } catch (Exception e) {
            throw new RuntimeException("静态地图接口请求失败：" + e.getMessage(), e);
        }
    }

    // ===================== 内置图片保存方法 =====================

    /**
     * 保存静态地图图片到指定文件路径
     *
     * @param resp     静态地图返回结果
     * @param savePath 保存路径，如 ./map/map.png
     * @return 生成的File对象
     */
    public File saveStaticMapImage(StaticMapResp resp, String savePath) {
        if (StrUtil.isBlank(savePath)) {
            throw new IllegalArgumentException("文件保存路径不能为空");
        }
        File file = FileUtil.file(savePath);
        return saveStaticMapImage(resp, file);
    }

    /**
     * 保存静态地图图片到File对象
     *
     * @param resp       静态地图返回结果
     * @param targetFile 目标文件
     * @return 写入后的文件
     */
    public File saveStaticMapImage(StaticMapResp resp, File targetFile) {
        // 校验返回图片字节
        if (resp == null || resp.getImageBytes() == null || resp.getImageBytes().length == 0) {
            throw new RuntimeException("无有效地图图片数据，无法保存，错误信息：" + (resp == null ? "" : resp.getRawText()));
        }
        if (targetFile == null) {
            throw new IllegalArgumentException("目标文件对象不能为null");
        }
        // 自动创建父文件夹
        FileUtil.mkParentDirs(targetFile);
        // 写入字节
        try {
            FileUtil.writeBytes(resp.getImageBytes(), targetFile);
        } catch (Exception e) {
            throw new RuntimeException("图片写入文件失败，路径：" + targetFile.getAbsolutePath(), e);
        }
        // 校验文件是否生成成功
        if (!targetFile.exists() || targetFile.length() <= 0) {
            throw new RuntimeException("文件写入完成但文件为空或未生成：" + targetFile.getAbsolutePath());
        }
        return targetFile;
    }

    // ===================== 参数校验私有方法 =====================
    private void validateStaticMapParam(StaticMapRequest request) {
        boolean hasOverlay = StrUtil.isNotBlank(request.getMarkers())
                || StrUtil.isNotBlank(request.getLabels())
                || StrUtil.isNotBlank(request.getPaths());
        if (!hasOverlay) {
            if (StrUtil.isBlank(request.getLocation())) {
                throw new IllegalArgumentException("静态地图无标注/折线时，location中心点必填");
            }
            if (request.getZoom() == null || request.getZoom() < 1 || request.getZoom() > 17) {
                throw new IllegalArgumentException("缩放级别zoom范围必须1~17");
            }
        }
        if (StrUtil.isNotBlank(request.getSize())) {
            String[] sizeArr = request.getSize().split("\\*");
            if (sizeArr.length != 2) {
                throw new IllegalArgumentException("size格式错误，示例：400*400");
            }
            try {
                int w = Integer.parseInt(sizeArr[0]);
                int h = Integer.parseInt(sizeArr[1]);
                if (w > 1024 || h > 1024) {
                    throw new IllegalArgumentException("图片宽高不能超过1024");
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("size宽高必须为数字");
            }
        }
        if (request.getScale() != null && request.getScale() != 1 && request.getScale() != 2) {
            throw new IllegalArgumentException("scale仅支持1、2");
        }
        if (request.getTraffic() != null && request.getTraffic() != 0 && request.getTraffic() != 1) {
            throw new IllegalArgumentException("traffic仅支持0、1");
        }
    }

}
