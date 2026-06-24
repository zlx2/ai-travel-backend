package com.sora.aitravel.client.amap;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.sora.aitravel.config.AmapProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 美食模块专用的高德地图客户端。
 *
 * <p>只负责美食模块所需的高德周边搜索、关键字搜索和地理编码，不处理用户意图、业务 DTO、响应构建或推荐理由。
 */
@Component
@RequiredArgsConstructor
public class AmapFoodClient {

    /** 高德餐饮服务大类编码。050000 表示餐饮服务。 */
    private static final String AMAP_FOOD_TYPE = "050000";

    /** 请求 business 扩展字段，里面可能包含评分、人均、标签、营业时间等信息。 */
    private static final String AMAP_BUSINESS_FIELDS = "business";

    /** 高德配置，读取 app.amap.api-key/base-url/timeout。 */
    private final AmapProperties amapProperties;

    /** 调用高德周边搜索：用于当前位置附近、景点附近这两类查询。 */
    public JSONObject searchAround(
            String location,
            String keywords,
            String region,
            Integer radius,
            Integer pageSize,
            Integer pageNum) {
        HttpRequest request =
                amapGet("/v5/place/around")
                        .form("location", location)
                        .form("types", AMAP_FOOD_TYPE)
                        .form("radius", radius)
                        .form("sortrule", "distance")
                        .form("show_fields", AMAP_BUSINESS_FIELDS)
                        .form("page_size", pageSize)
                        .form("page_num", pageNum);
        if (StringUtils.hasText(keywords)) {
            request.form("keywords", keywords);
        }
        if (StringUtils.hasText(region)) {
            request.form("region", region).form("city_limit", "true");
        }
        return executeJson(request);
    }

    /** 调用高德关键字搜索：用于“重庆火锅推荐”这类城市美食查询。 */
    public JSONObject searchText(String city, String keywords, Integer pageSize, Integer pageNum) {
        return executeJson(
                amapGet("/v5/place/text")
                        .form("keywords", keywords)
                        .form("region", city)
                        .form("city_limit", "true")
                        .form("types", AMAP_FOOD_TYPE)
                        .form("show_fields", AMAP_BUSINESS_FIELDS)
                        .form("page_size", pageSize)
                        .form("page_num", pageNum));
    }

    /** 调用高德地理编码，把文字地点转成经纬度。 */
    public String geocodeToLocation(String address, String city) {
        JSONObject json = geocode(address, city);
        if (!isAmapSuccess(json)) {
            throw new IllegalStateException("地理编码失败：" + text(json, "info"));
        }
        JSONArray geocodes = json.getJSONArray("geocodes");
        if (geocodes == null || geocodes.isEmpty()) {
            throw new IllegalStateException("未解析到地点坐标");
        }
        String location = text(geocodes.getJSONObject(0), "location");
        if (!StringUtils.hasText(location)) {
            throw new IllegalStateException("地点坐标为空");
        }
        return location;
    }

    /** 判断高德接口是否成功。status=1 且 infocode=10000 表示成功。 */
    public boolean isAmapSuccess(JSONObject json) {
        return json != null
                && "1".equals(text(json, "status"))
                && "10000".equals(text(json, "infocode"));
    }

    /** 判断是否缺少高德 API Key。 */
    public boolean missingApiKey() {
        return amapProperties.getApiKey() == null || amapProperties.getApiKey().isBlank();
    }

    /** 调用高德地理编码接口。 */
    private JSONObject geocode(String address, String city) {
        HttpRequest request = amapGet("/v3/geocode/geo").form("address", address);
        if (StringUtils.hasText(city)) {
            request.form("city", city);
        }
        return executeJson(request);
    }

    /** 构造高德 GET 请求，统一拼接 baseUrl、key 和 timeout。 */
    private HttpRequest amapGet(String path) {
        String baseUrl = amapProperties.getBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            baseUrl = "https://restapi.amap.com";
        }
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        int timeoutMillis =
                amapProperties.getTimeout() == null
                        ? 10000
                        : Math.toIntExact(amapProperties.getTimeout().toMillis());
        return HttpRequest.get(baseUrl + path)
                .timeout(timeoutMillis)
                .form("key", amapProperties.getApiKey());
    }

    /** 执行 HTTP 请求，并把响应体解析成 Hutool JSONObject。 */
    private JSONObject executeJson(HttpRequest request) {
        try (HttpResponse response = request.execute()) {
            return JSONUtil.parseObj(response.body());
        }
    }

    /** 从高德 JSONObject 中安全读取字符串。 */
    private String text(JSONObject object, String fieldName) {
        if (object == null) {
            return "";
        }
        Object value = object.get(fieldName);
        if (value == null) {
            return "";
        }
        if (value instanceof JSONArray array && array.isEmpty()) {
            return "";
        }
        return String.valueOf(value);
    }
}
