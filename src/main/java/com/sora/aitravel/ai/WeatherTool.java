package com.sora.aitravel.ai;

import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 天气查询工具 —— 通过 Open-Meteo 获取实时天气 + 16 天预报。
 *
 * <p>Open-Meteo 是开源免费天气 API，无需 API Key。 文档：https://open-meteo.com/
 */
@Slf4j
@Component
public class WeatherTool {

    private static final String GEO_API =
            "https://geocoding-api.open-meteo.com/v1/search"; // 地理编码接口，把城市名转化成经纬度
    private static final String FORECAST_API =
            "https://api.open-meteo.com/v1/forecast"; // 天气预报接口，根据经纬度获取天气数据

    // 世界气象组织 WMO 天气代码映射
    private static final Map<Integer, String> WMO_CODES = new HashMap<>();

    static {
        WMO_CODES.put(0, "晴");
        WMO_CODES.put(1, "大部晴朗");
        WMO_CODES.put(2, "多云");
        WMO_CODES.put(3, "阴天");
        WMO_CODES.put(45, "雾");
        WMO_CODES.put(48, "雾凇");
        WMO_CODES.put(51, "小雨");
        WMO_CODES.put(53, "中雨");
        WMO_CODES.put(55, "大雨");
        WMO_CODES.put(56, "冻毛毛雨");
        WMO_CODES.put(61, "小雨");
        WMO_CODES.put(63, "中雨");
        WMO_CODES.put(65, "大雨");
        WMO_CODES.put(66, "冻雨");
        WMO_CODES.put(67, "冻雨");
        WMO_CODES.put(71, "小雪");
        WMO_CODES.put(73, "中雪");
        WMO_CODES.put(75, "大雪");
        WMO_CODES.put(77, "雪粒");
        WMO_CODES.put(80, "小阵雨");
        WMO_CODES.put(81, "中阵雨");
        WMO_CODES.put(82, "大阵雨");
        WMO_CODES.put(85, "小阵雪");
        WMO_CODES.put(86, "大阵雪");
        WMO_CODES.put(95, "雷暴");
        WMO_CODES.put(96, "雷暴伴小冰雹");
        WMO_CODES.put(99, "雷暴伴大冰雹");
    }

    @Tool(description = "查询指定城市的实时天气和未来16天天气预报，包括温度、天气状况等。" + "当用户询问某个城市或目的地的天气、未来几天天气怎么样时调用此工具。")
    public String getWeather(
            @ToolParam(description = "城市名称，支持中文或英文，例如：北京、上海、Tokyo、Paris") String city) {

        log.info("WeatherTool.getWeather 被调用，城市：{}", city);

        try {
            // 第一步：通过城市名获取经纬度
            double[] coords = geocode(city);
            if (coords == null) {
                log.warn("未找到城市经纬度：{}", city);
                JSONObject error = new JSONObject();
                error.set("city", city);
                error.set("error", "未找到城市，请检查城市名称");
                return error.toString();
            }

            log.info("城市 {} 经纬度：{}, {}", city, coords[0], coords[1]);

            double lat = coords[0];
            double lon = coords[1];

            // 第二步：获取 7 天预报（含当前天气）
            String forecastUrl =
                    FORECAST_API
                            + "?latitude="
                            + lat // 纬度参数
                            + "&longitude="
                            + lon // 经度参数
                            + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m,wind_direction_10m" // 当前天气数据字段：温度、相对湿度、体感温度、天气代码、风速、风向
                            + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,wind_speed_10m_max" // 每日预报数据字段：天气代码、最高温、最低温、降水量、最大风速
                            + "&timezone=auto" // 时区设置为自动（使用当地时区）
                            + "&forecast_days=7"; // 预报天数：7天

            String response =
                    HttpRequest.get(forecastUrl)
                            .header("Accept-Encoding", "identity")
                            .timeout(10000)
                            .execute()
                            .body();

            return parseForecast(city, response);
        } catch (Exception e) {
            log.error("天气查询失败，城市：{}", city, e);
            JSONObject error = new JSONObject();
            error.set("city", city);
            error.set("error", "查询天气失败：" + e.getMessage());
            return error.toString();
        }
    }

    /** 通过城市名获取经纬度。 */
    private double[] geocode(String city) {
        try {
            log.info("调用地理编码 API，城市：{}", city);
            String response =
                    HttpRequest.get(GEO_API)
                            .form("name", city)
                            .form("count", 1)
                            .form("language", "zh")
                            .form("format", "json")
                            .header("Accept-Encoding", "identity")
                            .timeout(8000)
                            .execute()
                            .body();

            log.info("地理编码 API 响应：{}", response.substring(0, Math.min(200, response.length())));

            JSONObject json = JSONUtil.parseObj(response);
            JSONArray results = json.getJSONArray("results");
            if (results != null && !results.isEmpty()) {
                JSONObject first = results.getJSONObject(0);
                return new double[] {first.getDouble("latitude"), first.getDouble("longitude")};
            }
            log.warn("地理编码 API 未返回结果");
        } catch (Exception e) {
            log.error("地理编码 API 调用失败", e);
        }
        return null;
    }

    /** 解析 Open-Meteo 预报响应，将数据转换成 JSON 格式返回。 */
    private String parseForecast(String city, String jsonResponse) {
        try {
            JSONObject json = JSONUtil.parseObj(jsonResponse);
            JSONObject result = new JSONObject();
            result.set("city", city);
            result.set("source", "Open-Meteo");

            // 当前天气
            JSONObject current = json.getJSONObject("current");
            if (current != null) {
                double temp = current.getDouble("temperature_2m");
                double feelsLike = current.getDouble("apparent_temperature");
                int humidity = current.getInt("relative_humidity_2m");
                int weatherCode = current.getInt("weather_code");
                double windSpeed = current.getDouble("wind_speed_10m");
                String weatherDesc = WMO_CODES.getOrDefault(weatherCode, "未知");

                JSONObject currentJson = new JSONObject();
                currentJson.set("weather", weatherDesc);
                currentJson.set("temperature", Math.round(temp));
                currentJson.set("feelsLike", Math.round(feelsLike));
                currentJson.set("humidity", humidity);
                currentJson.set("windSpeed", Math.round(windSpeed));
                result.set("current", currentJson);
            }

            // 7 天预报
            JSONObject daily = json.getJSONObject("daily");
            if (daily != null) {
                JSONArray timeArr = daily.getJSONArray("time");
                JSONArray maxTempArr = daily.getJSONArray("temperature_2m_max");
                JSONArray minTempArr = daily.getJSONArray("temperature_2m_min");
                JSONArray weatherCodeArr = daily.getJSONArray("weather_code");
                JSONArray precipArr = daily.getJSONArray("precipitation_sum");

                JSONArray forecastArr = new JSONArray();
                for (int i = 0; i < timeArr.size(); i++) {
                    String date = timeArr.getStr(i);
                    double maxTemp = maxTempArr.getDouble(i);
                    double minTemp = minTempArr.getDouble(i);
                    int weatherCode = weatherCodeArr.getInt(i);
                    double precip = precipArr.getDouble(i);
                    String weatherDesc = WMO_CODES.getOrDefault(weatherCode, "未知");

                    String dateLabel;
                    if (i == 0) {
                        dateLabel = "今天";
                    } else if (i == 1) {
                        dateLabel = "明天";
                    } else if (i == 2) {
                        dateLabel = "后天";
                    } else {
                        dateLabel = date;
                    }

                    JSONObject dayForecast = new JSONObject();
                    dayForecast.set("date", date);
                    dayForecast.set("label", dateLabel);
                    dayForecast.set("weather", weatherDesc);
                    dayForecast.set("tempMin", Math.round(minTemp));
                    dayForecast.set("tempMax", Math.round(maxTemp));
                    if (precip > 0) {
                        dayForecast.set(
                                "precipitation", Double.parseDouble(String.format("%.1f", precip)));
                    }
                    forecastArr.add(dayForecast);
                }
                result.set("forecastDays", timeArr.size());
                result.set("forecast", forecastArr);
            }

            return result.toString();
        } catch (Exception e) {
            JSONObject error = new JSONObject();
            error.set("city", city);
            error.set("error", "天气数据解析失败：" + e.getMessage());
            return error.toString();
        }
    }
}
