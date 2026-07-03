package com.sora.aitravel.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.sora.aitravel.config.AmapProperties;
import com.sora.aitravel.dto.model.AmapApiResp;
import com.sora.aitravel.dto.model.poi.Poi;
import com.sora.aitravel.dto.model.poi.PoiBusiness;
import com.sora.aitravel.dto.model.poi.PoiNavi;
import com.sora.aitravel.dto.model.poi.PoiPhoto;
import com.sora.aitravel.service.AmapApiService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 高德地图API服务实现
 * 提供POI搜索、地理编码等高德地图API封装功能
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AmapApiServiceImpl implements AmapApiService {
    private final AmapProperties amapProperties;

    /**
     * 搜索POI文本
     *
     * @param keywords   搜索关键词
     * @param types      POI类型
     * @param region     区域
     * @param cityLimit  是否限制在城市内
     * @param pageSize   每页大小
     * @param pageNum    页码
     * @param showFields 显示字段
     * @return POI搜索结果
     */
    @Override
    public AmapApiResp<List<Poi>> searchPoiText(
            String keywords,
            String types,
            String region,
            Boolean cityLimit,
            Integer pageSize,
            Integer pageNum,
            String showFields) {
        return parsePoiResponse(
                searchPoiTextRaw(keywords, types, region, cityLimit, pageSize, pageNum, showFields)
                        .toString());
    }

    /**
     * 搜索POI文本（原始JSON）
     *
     * @param keywords   搜索关键词
     * @param types      POI类型
     * @param region     区域
     * @param cityLimit  是否限制在城市内
     * @param pageSize   每页大小
     * @param pageNum    页码
     * @param showFields 显示字段
     * @return 原始JSON响应
     */
    @Override
    public JSONObject searchPoiTextRaw(
            String keywords,
            String types,
            String region,
            Boolean cityLimit,
            Integer pageSize,
            Integer pageNum,
            String showFields) {
        long start = System.currentTimeMillis();
        if (StrUtil.isBlank(keywords) && StrUtil.isBlank(types)) {
            throw new IllegalArgumentException("keywords和types至少需要提供一个");
        }
        Map<String, Object> params = new HashMap<>();
        params.put("key", apiKey());
        putIfNotBlank(params, "keywords", keywords);
        putIfNotBlank(params, "types", types);
        putIfNotBlank(params, "region", region);
        putIfNotBlank(params, "show_fields", showFields);
        if (cityLimit != null) {
            params.put("city_limit", cityLimit);
        }
        if (pageSize != null) {
            params.put("page_size", Math.max(1, Math.min(pageSize, 25)));
        }
        if (pageNum != null) {
            params.put("page_num", Math.max(1, pageNum));
        }
        JSONObject result = JSONUtil.parseObj(executeGet(baseUrl() + "/v5/place/text", params));
        log.info(
                "高德 text 查询完成，耗时={}ms，keywords={}, types={}, region={}, cityLimit={}, pageSize={}, pageNum={}, count={}, status={}, info={}",
                System.currentTimeMillis() - start,
                keywords,
                types,
                region,
                cityLimit,
                pageSize,
                pageNum,
                result.getStr("count"),
                result.getStr("status"),
                result.getStr("info"));
        return result;
    }

    /**
     * 周边POI搜索（原始JSON）
     *
     * @param location   中心点坐标
     * @param keywords   搜索关键词
     * @param types      POI类型
     * @param region     区域
     * @param cityLimit  是否限制在城市内
     * @param radius     搜索半径
     * @param sortrule   排序规则
     * @param pageSize   每页大小
     * @param pageNum    页码
     * @param showFields 显示字段
     * @return 原始JSON响应
     */
    @Override
    public JSONObject searchPoiAroundRaw(
            String location,
            String keywords,
            String types,
            String region,
            Boolean cityLimit,
            Integer radius,
            String sortrule,
            Integer pageSize,
            Integer pageNum,
            String showFields) {
        long start = System.currentTimeMillis();
        Map<String, Object> params = new HashMap<>();
        params.put("key", apiKey());
        putIfNotBlank(params, "location", location);
        putIfNotBlank(params, "keywords", keywords);
        putIfNotBlank(params, "types", types);
        putIfNotBlank(params, "region", region);
        putIfNotBlank(params, "show_fields", showFields);
        putIfNotBlank(params, "sortrule", sortrule);
        if (cityLimit != null) {
            params.put("city_limit", String.valueOf(cityLimit));
        }
        if (radius != null) {
            params.put("radius", radius);
        }
        if (pageSize != null) {
            params.put("page_size", Math.max(1, Math.min(pageSize, 25)));
        }
        if (pageNum != null) {
            params.put("page_num", Math.max(1, pageNum));
        }
        JSONObject result = JSONUtil.parseObj(executeGet(baseUrl() + "/v5/place/around", params));
        log.info(
                "高德 around 查询完成，耗时={}ms，location={}, keywords={}, types={}, region={}, cityLimit={}, radius={}, sortrule={}, pageSize={}, pageNum={}, count={}, status={}, info={}",
                System.currentTimeMillis() - start,
                location,
                keywords,
                types,
                region,
                cityLimit,
                radius,
                sortrule,
                pageSize,
                pageNum,
                result.getStr("count"),
                result.getStr("status"),
                result.getStr("info"));
        return result;
    }

    /**
     * 地理编码（原始JSON）
     *
     * @param address 地址
     * @param city    城市
     * @return 原始JSON响应
     */
    @Override
    public JSONObject geocodeRaw(String address, String city) {
        long start = System.currentTimeMillis();
        Map<String, Object> params = new HashMap<>();
        params.put("key", apiKey());
        putIfNotBlank(params, "address", address);
        putIfNotBlank(params, "city", city);
        JSONObject result = JSONUtil.parseObj(executeGet(baseUrl() + "/v3/geocode/geo", params));
        log.info(
                "高德 geocode 查询完成，耗时={}ms，address={}, city={}, count={}, status={}, info={}",
                System.currentTimeMillis() - start,
                address,
                city,
                result.getStr("count"),
                result.getStr("status"),
                result.getStr("info"));
        return result;
    }

    /**
     * 获取高德API基础URL
     *
     * @return 基础URL
     */
    private String baseUrl() {
        return StrUtil.blankToDefault(amapProperties.getBaseUrl(), "https://restapi.amap.com");
    }

    /**
     * 获取API密钥
     *
     * @return API密钥
     */
    private String apiKey() {
        return StrUtil.blankToDefault(amapProperties.getApiKey(), System.getenv("AMAP_API_KEY"));
    }

    /**
     * 获取超时时间
     *
     * @return 超时时间
     */
    private Duration timeout() {
        return amapProperties.getTimeout() == null
                ? Duration.ofSeconds(10)
                : amapProperties.getTimeout();
    }

    /**
     * 非空时添加参数
     *
     * @param params 参数Map
     * @param key    键
     * @param value  值
     */
    private void putIfNotBlank(Map<String, Object> params, String key, String value) {
        if (StrUtil.isNotBlank(value)) {
            params.put(key, value);
        }
    }

    /**
     * 执行GET请求
     *
     * @param url    请求URL
     * @param params 请求参数
     * @return 响应体
     */
    private String executeGet(String url, Map<String, Object> params) {
        try {
            HttpRequest request =
                    HttpUtil.createGet(url).timeout((int) timeout().toMillis()).form(params);
            try (HttpResponse response = request.execute()) {
                String body = response.body();
                log.debug("高德 POI 请求 url={}, response={}", request.getUrl(), body);
                return body;
            }
        } catch (Exception exception) {
            throw new RuntimeException("请求高德 POI 失败：" + exception.getMessage(), exception);
        }
    }

    /**
     * 解析POI响应
     *
     * @param json 原始JSON字符串
     * @return 解析后的POI响应对象
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
            if (!result.isSuccess()) {
                return result;
            }
            JSONArray poisArray = obj.getJSONArray("pois");
            if (poisArray == null) {
                result.setData(List.of());
                return result;
            }
            List<Poi> pois = new ArrayList<>();
            for (Object poiObj : poisArray) {
                JSONObject poiJson = (JSONObject) poiObj;
                Poi poi = JSONUtil.toBean(poiJson, Poi.class);
                poi.setBusiness(parseBusiness(poiJson.getJSONObject("business")));
                poi.setNavi(parseNavi(poiJson.getJSONObject("navi")));
                poi.setPhotos(parsePhotos(poiJson.getJSONArray("photos")));
                pois.add(poi);
            }
            result.setData(pois);
        } catch (Exception exception) {
            log.error("解析高德 POI 响应失败", exception);
            result.setStatus("0");
            result.setInfo("解析响应失败");
            result.setData(List.of());
        }
        return result;
    }

    /**
     * 解析商家信息
     *
     * @param business JSON对象
     * @return 商家信息对象
     */
    private PoiBusiness parseBusiness(JSONObject business) {
        if (business == null) {
            return null;
        }
        PoiBusiness value = new PoiBusiness();
        value.setBusinessArea(business.getStr("business_area"));
        value.setOpentimeToday(business.getStr("opentime_today"));
        value.setOpentimeWeek(business.getStr("opentime_week"));
        value.setTel(business.getStr("tel"));
        value.setTag(business.getStr("tag"));
        value.setRating(business.getStr("rating"));
        value.setCost(business.getStr("cost"));
        value.setAlias(business.getStr("alias"));
        value.setKeytag(business.getStr("keytag"));
        value.setRectag(business.getStr("rectag"));
        return value;
    }

    /**
     * 解析导航信息
     *
     * @param navi JSON对象
     * @return 导航信息对象
     */
    private PoiNavi parseNavi(JSONObject navi) {
        if (navi == null) {
            return null;
        }
        PoiNavi value = new PoiNavi();
        value.setNaviPoiid(navi.getStr("navi_poiid"));
        value.setEntrLocation(navi.getStr("entr_location"));
        value.setExitLocation(navi.getStr("exit_location"));
        value.setGridcode(navi.getStr("gridcode"));
        return value;
    }

    /**
     * 解析图片列表
     *
     * @param photos JSON数组
     * @return 图片列表
     */
    private List<PoiPhoto> parsePhotos(JSONArray photos) {
        if (photos == null) {
            return List.of();
        }
        return photos.stream()
                .map(JSONObject.class::cast)
                .map(
                        photo -> {
                            PoiPhoto value = new PoiPhoto();
                            value.setTitle(photo.getStr("title"));
                            value.setUrl(photo.getStr("url"));
                            return value;
                        })
                .toList();
    }
}
