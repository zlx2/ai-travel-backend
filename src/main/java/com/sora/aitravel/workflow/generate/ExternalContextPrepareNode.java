package com.sora.aitravel.workflow.generate;

import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.HOTEL_SEARCH_RESULT;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.REQUIREMENT;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.WEATHER_FORECAST;

import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.service.AmapApiService;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalContextPrepareNode {

    private static final String OPEN_METEO_GEO_URL =
            "https://geocoding-api.open-meteo.com/v1/search";
    private static final String OPEN_METEO_FORECAST_URL = "https://api.open-meteo.com/v1/forecast";
    private static final Map<Integer, String> WEATHER_CODES = weatherCodes();

    private final AmapApiService amapApiService;

    public Map<String, Object> execute(OverAllState state) {
        TravelRequirementDTO requirement =
                TripGraphStateCodec.required(state, REQUIREMENT, TravelRequirementDTO.class);
        return TripGraphStateCodec.patch(
                WEATHER_FORECAST, fetchWeather(requirement),
                HOTEL_SEARCH_RESULT, fetchHotels(requirement));
    }

    private String fetchWeather(TravelRequirementDTO requirement) {
        String destination = primarySearchCity(requirement);
        if (destination == null || destination.isBlank()) {
            log.warn("节点[external-context-prepare]：目的地为空，跳过天气查询");
            return null;
        }
        try {
            log.info("节点[external-context-prepare]：查询 {} 天气", destination);
            String data = queryWeather(destination);
            log.info("节点[external-context-prepare]：天气获取成功，长度={}", data.length());
            return data;
        } catch (Exception e) {
            log.error("节点[external-context-prepare]：天气查询失败，destination={}", destination, e);
            return "天气数据暂不可用：" + e.getMessage();
        }
    }

    private String fetchHotels(TravelRequirementDTO requirement) {
        String destination = primarySearchCity(requirement);
        if (destination == null || destination.isBlank()) {
            log.warn("节点[external-context-prepare]：目的地为空，跳过酒店查询");
            return null;
        }
        String travelDate = requirement.getTravelDate();
        LocalDate checkIn =
                travelDate != null ? LocalDate.parse(travelDate) : LocalDate.now().plusDays(1);
        LocalDate checkOut =
                checkIn.plusDays(requirement.getDays() != null ? requirement.getDays() : 3);
        String checkInStr = checkIn.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String checkOutStr = checkOut.format(DateTimeFormatter.ISO_LOCAL_DATE);
        try {
            log.info(
                    "节点[external-context-prepare]：查询 {} 酒店，入住={}，离店={}",
                    destination,
                    checkInStr,
                    checkOutStr);
            String data = queryHotels(destination, checkInStr, checkOutStr);
            log.info("节点[external-context-prepare]：酒店获取成功，长度={}", data.length());
            return data;
        } catch (Exception e) {
            log.error("节点[external-context-prepare]：酒店查询失败，destination={}", destination, e);
            return "酒店数据暂不可用：" + e.getMessage();
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
                                .timeout(8000)
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
                        .timeout(10000)
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

    private String queryHotels(String city, String checkIn, String checkOut) {
        JSONObject json =
                amapApiService.searchPoiTextRaw("酒店", "100100", city, true, 8, 1, "business");
        if (!"1".equals(json.getStr("status"))) {
            return JSONUtil.createObj()
                    .set("city", city)
                    .set("error", "高德酒店查询失败：" + json.getStr("info", "unknown"))
                    .toString();
        }
        JSONArray hotels = new JSONArray();
        JSONArray pois = json.getJSONArray("pois");
        for (int i = 0; pois != null && i < Math.min(pois.size(), 6); i++) {
            JSONObject poi = pois.getJSONObject(i);
            hotels.add(
                    JSONUtil.createObj()
                            .set("name", poi.getStr("name"))
                            .set("address", poi.getStr("address"))
                            .set("tel", poi.getStr("tel"))
                            .set("type", poi.getStr("type"))
                            .set("rating", poi.getByPath("biz_ext.rating"))
                            .set("location", poi.getStr("location")));
        }
        return JSONUtil.createObj()
                .set("city", city)
                .set("checkIn", checkIn)
                .set("checkOut", checkOut)
                .set("source", "高德地图")
                .set("hotels", hotels)
                .toString();
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
}
