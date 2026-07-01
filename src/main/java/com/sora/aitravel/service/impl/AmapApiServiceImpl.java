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

@Service
@RequiredArgsConstructor
@Slf4j
public class AmapApiServiceImpl implements AmapApiService {
    private final AmapProperties amapProperties;

    @Override
    public AmapApiResp<List<Poi>> searchPoiText(
            String keywords,
            String types,
            String region,
            Boolean cityLimit,
            Integer pageSize,
            Integer pageNum,
            String showFields) {
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
        return parsePoiResponse(executeGet(baseUrl() + "/v5/place/text", params));
    }

    private String baseUrl() {
        return StrUtil.blankToDefault(amapProperties.getBaseUrl(), "https://restapi.amap.com");
    }

    private String apiKey() {
        return StrUtil.blankToDefault(amapProperties.getApiKey(), System.getenv("AMAP_API_KEY"));
    }

    private Duration timeout() {
        return amapProperties.getTimeout() == null ? Duration.ofSeconds(10) : amapProperties.getTimeout();
    }

    private void putIfNotBlank(Map<String, Object> params, String key, String value) {
        if (StrUtil.isNotBlank(value)) {
            params.put(key, value);
        }
    }

    private String executeGet(String url, Map<String, Object> params) {
        try {
            HttpRequest request = HttpUtil.createGet(url).timeout((int) timeout().toMillis()).form(params);
            try (HttpResponse response = request.execute()) {
                String body = response.body();
                log.debug("高德 POI 请求 url={}, response={}", request.getUrl(), body);
                return body;
            }
        } catch (Exception exception) {
            throw new RuntimeException("请求高德 POI 失败：" + exception.getMessage(), exception);
        }
    }

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

    private List<PoiPhoto> parsePhotos(JSONArray photos) {
        if (photos == null) {
            return List.of();
        }
        return photos.stream()
                .map(JSONObject.class::cast)
                .map(photo -> {
                    PoiPhoto value = new PoiPhoto();
                    value.setTitle(photo.getStr("title"));
                    value.setUrl(photo.getStr("url"));
                    return value;
                })
                .toList();
    }
}
