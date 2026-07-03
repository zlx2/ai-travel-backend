package com.sora.aitravel.workflow.generate;

import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.REQUIREMENT;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.WEATHER_FORECAST;

import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ExternalContextPrepareNode {

    private static final String OPEN_METEO_GEO_URL =
            "https://geocoding-api.open-meteo.com/v1/search";
    private static final String OPEN_METEO_FORECAST_URL = "https://api.open-meteo.com/v1/forecast";
    private static final Map<Integer, String> WEATHER_CODES = weatherCodes();
    private static final Map<String, WeatherCacheEntry> WEATHER_CACHE = new ConcurrentHashMap<>();

    @Value("${app.weather.cache-ttl:6h}")
    private Duration weatherCacheTtl;

    @Value("${app.weather.geo-timeout-ms:2500}")
    private int geoTimeoutMs;

    @Value("${app.weather.forecast-timeout-ms:3000}")
    private int forecastTimeoutMs;

    public Map<String, Object> execute(OverAllState state) {
        TravelRequirementDTO requirement =
                TripGraphStateCodec.required(state, REQUIREMENT, TravelRequirementDTO.class);
        return TripGraphStateCodec.patch(WEATHER_FORECAST, fetchWeather(requirement));
    }

    private String fetchWeather(TravelRequirementDTO requirement) {
        String destination = primarySearchCity(requirement);
        if (destination == null || destination.isBlank()) {
            log.warn("节点[external-context-prepare]：目的地为空，跳过天气查询");
            return null;
        }
        try {
            WeatherCacheEntry cached = WEATHER_CACHE.get(destination);
            if (cached != null && !cached.expired(weatherCacheTtl)) {
                log.info("节点[external-context-prepare]：天气缓存命中，destination={}", destination);
                return cached.data();
            }
            log.info("节点[external-context-prepare]：查询 {} 天气", destination);
            String data = queryWeather(destination);
            WEATHER_CACHE.put(destination, new WeatherCacheEntry(data, System.currentTimeMillis()));
            log.info("节点[external-context-prepare]：天气获取成功，长度={}", data.length());
            return data;
        } catch (Exception e) {
            log.error("节点[external-context-prepare]：天气查询失败，destination={}", destination, e);
            return "天气数据暂不可用：" + e.getMessage();
        }
    }

    private String primarySearchCity(TravelRequirementDTO requirement) {
        if (requirement.getRouteCities() != null && !requirement.getRouteCities().isEmpty()) {
            return requirement.getRouteCities().get(0);
        }
        if (requirement.getRouteRegion() != null && !requirement.getRouteRegion().isBlank()) {
            return requirement.getRouteRegion();
        }
        return requirement.getDestination();
    }

    private String queryWeather(String city) {
        JSONObject geo =
                JSONUtil.parseObj(
                        HttpRequest.get(OPEN_METEO_GEO_URL)
                                .form("name", city)
                                .form("count", 1)
                                .form("language", "zh")
                                .form("format", "json")
                                .header("Accept-Encoding", "identity")
                                .timeout(geoTimeoutMs)
                                .execute()
                                .body());
        JSONArray results = geo.getJSONArray("results");
        if (results == null || results.isEmpty()) {
            return JSONUtil.createObj().set("city", city).set("error", "未找到天气城市").toString();
        }
        JSONObject first = results.getJSONObject(0);
        String response =
                HttpRequest.get(OPEN_METEO_FORECAST_URL)
                        .form("latitude", first.getDouble("latitude"))
                        .form("longitude", first.getDouble("longitude"))
                        .form(
                                "current",
                                "temperature_2m,apparent_temperature,weather_code,wind_speed_10m")
                        .form(
                                "daily",
                                "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum")
                        .form("timezone", "auto")
                        .form("forecast_days", 7)
                        .header("Accept-Encoding", "identity")
                        .timeout(forecastTimeoutMs)
                        .execute()
                        .body();
        JSONObject forecast = JSONUtil.parseObj(response);
        JSONObject result = JSONUtil.createObj().set("city", city).set("source", "Open-Meteo");
        JSONObject current = forecast.getJSONObject("current");
        if (current != null) {
            int code = current.getInt("weather_code", -1);
            result.set(
                    "current",
                    JSONUtil.createObj()
                            .set("weather", WEATHER_CODES.getOrDefault(code, "未知"))
                            .set(
                                    "temperature",
                                    Math.round(current.getDouble("temperature_2m", 0.0)))
                            .set(
                                    "feelsLike",
                                    Math.round(current.getDouble("apparent_temperature", 0.0)))
                            .set(
                                    "windSpeed",
                                    Math.round(current.getDouble("wind_speed_10m", 0.0))));
        }
        JSONObject daily = forecast.getJSONObject("daily");
        if (daily != null) {
            JSONArray times = daily.getJSONArray("time");
            JSONArray maxTemps = daily.getJSONArray("temperature_2m_max");
            JSONArray minTemps = daily.getJSONArray("temperature_2m_min");
            JSONArray codes = daily.getJSONArray("weather_code");
            JSONArray precipitation = daily.getJSONArray("precipitation_sum");
            JSONArray days = new JSONArray();
            for (int i = 0; times != null && i < times.size(); i++) {
                int code = codes == null ? -1 : codes.getInt(i);
                days.add(
                        JSONUtil.createObj()
                                .set("date", times.getStr(i))
                                .set("weather", WEATHER_CODES.getOrDefault(code, "未知"))
                                .set("tempMin", Math.round(minTemps.getDouble(i)))
                                .set("tempMax", Math.round(maxTemps.getDouble(i)))
                                .set(
                                        "precipitation",
                                        precipitation == null ? 0 : precipitation.getDouble(i)));
            }
            result.set("forecastDays", days.size()).set("forecast", days);
        }
        return result.toString();
    }

    private static Map<Integer, String> weatherCodes() {
        Map<Integer, String> codes = new LinkedHashMap<>();
        codes.put(0, "晴");
        codes.put(1, "大部晴朗");
        codes.put(2, "多云");
        codes.put(3, "阴天");
        codes.put(45, "雾");
        codes.put(48, "雾凇");
        codes.put(51, "小雨");
        codes.put(53, "中雨");
        codes.put(55, "大雨");
        codes.put(61, "小雨");
        codes.put(63, "中雨");
        codes.put(65, "大雨");
        codes.put(71, "小雪");
        codes.put(73, "中雪");
        codes.put(75, "大雪");
        codes.put(80, "阵雨");
        codes.put(95, "雷暴");
        return Map.copyOf(codes);
    }

    private record WeatherCacheEntry(String data, long createdAtMs) {
        private boolean expired(Duration ttl) {
            Duration safeTtl = ttl == null || ttl.isNegative() || ttl.isZero() ? Duration.ofHours(6) : ttl;
            return System.currentTimeMillis() - createdAtMs > safeTtl.toMillis();
        }
    }
}
