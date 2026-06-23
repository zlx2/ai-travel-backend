package com.sora.aitravel.client;

import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.sora.aitravel.config.AmapProperties;
import org.springframework.stereotype.Component;

/**
 * 高德 POI 通用客户端。
 *
 * <p>该客户端只负责调用高德 Web Service POI 搜索接口，并做统一的 API Key、网关地址、超时和错误处理。
 * 业务侧不要在这里写酒店、景点、租车等筛选规则；不同业务应在自己的 Service 中消费这些通用 POI 结果。
 */
@Component
public class AmapPoiClient {

    private final AmapProperties properties;

    public AmapPoiClient(AmapProperties properties) {
        this.properties = properties;
    }

    /**
     * 按关键字搜索目标地点，并返回最适合作为中心点的 POI。
     *
     * <p>优先选择 parent 为空的主 POI，避免定位到站内子点位、出入口等过细位置。
     *
     * @param keywords 目标地点关键字，例如“成都东站”
     * @param cityName 城市名称，例如“成都市”
     * @return 目标地点 POI
     */
    public JSONObject searchFirstPoi(String keywords, String cityName) {
        JSONObject result =
                get("/v5/place/text")
                        .form("keywords", keywords)
                        .form("region", cityName)
                        .form("city_limit", "true")
                        .form("page_size", "5")
                        .form("show_fields", "business,navi")
                        .executeJson();

        checkAmapResult(result);

        JSONArray pois = result.getJSONArray("pois");
        if (pois == null || pois.isEmpty()) {
            throw new IllegalStateException("没有找到目标地点：" + keywords);
        }

        for (Object item : pois) {
            JSONObject poi = (JSONObject) item;
            if (text(poi, "parent").isBlank()) {
                return poi;
            }
        }

        return pois.getJSONObject(0);
    }

    /**
     * 以指定坐标为中心搜索周边 POI。
     *
     * @param location 高德坐标，经纬度格式为 lng,lat
     * @param keywords 搜索关键字，例如“租车”“酒店”“停车场”
     * @param cityName 城市名称
     * @param radius 搜索半径，单位米
     * @param pageSize 返回数量
     * @return 高德周边搜索原始结果
     */
    public JSONObject searchAround(
            String location, String keywords, String cityName, int radius, int pageSize) {
        JSONObject result =
                get("/v5/place/around")
                        .form("location", location)
                        .form("radius", String.valueOf(radius))
                        .form("keywords", keywords)
                        .form("region", cityName)
                        .form("city_limit", "true")
                        .form("page_size", String.valueOf(pageSize))
                        .form("show_fields", "business,navi")
                        .executeJson();

        checkAmapResult(result);
        return result;
    }

    private AmapRequest get(String path) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("未配置高德地图 API Key：app.amap.api-key");
        }
        if (properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()) {
            throw new IllegalStateException("未配置高德地图网关地址：app.amap.base-url");
        }
        if (properties.getTimeout() == null) {
            throw new IllegalStateException("未配置高德地图请求超时时间：app.amap.timeout");
        }
        return new AmapRequest(endpoint(path), properties.getTimeout().toMillis())
                .form("key", properties.getApiKey());
    }

    private String endpoint(String path) {
        String baseUrl = properties.getBaseUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + path;
    }

    private void checkAmapResult(JSONObject result) {
        if (result == null) {
            throw new IllegalStateException("高德接口返回为空");
        }

        String status = text(result, "status");
        if (!"1".equals(status)) {
            throw new IllegalStateException(
                    "高德接口调用失败，info=" + text(result, "info") + ", infocode=" + text(result, "infocode"));
        }
    }

    private String text(JSONObject object, String fieldName) {
        Object value = object.get(fieldName);
        return value == null ? "" : String.valueOf(value);
    }

    /** 小型请求包装，避免每个高德调用重复拼接 key、超时和 JSON 解析代码。 */
    private static class AmapRequest {
        private final HttpRequest request;

        private AmapRequest(String url, long timeoutMillis) {
            this.request = HttpRequest.get(url).timeout(Math.toIntExact(timeoutMillis));
        }

        private AmapRequest form(String name, String value) {
            request.form(name, value);
            return this;
        }

        private JSONObject executeJson() {
            String body = request.execute().body();
            return JSONUtil.parseObj(body);
        }
    }
}
