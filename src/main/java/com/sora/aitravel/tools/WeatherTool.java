package com.sora.aitravel.tools;

import cn.hutool.http.HttpRequest;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 天气查询工具 —— AI 可通过 Tool Calling 自动调用此工具获取实时天气信息。
 * <p>
 * 使用 wttr.in 免费天气服务，无需 API Key。
 * 文档：https://github.com/chubin/wttr.in
 * </p>
 */
@Component
public class WeatherTool {

    @Tool(description = "查询指定城市的实时天气信息，包括温度、天气状况、风速、湿度等。"
            + "当用户询问某个城市或目的地的天气时调用此工具。")
    public String getWeather(
            @ToolParam(description = "城市名称，支持中文或英文，例如：北京、上海、Tokyo、Paris") String city) {

        try {
            // 获取 JSON 格式的详细天气数据
            String jsonUrl = "https://wttr.in/" + city + "?format=j1&lang=zh";
            String jsonResponse = HttpRequest.get(jsonUrl)
                    .timeout(10000)
                    .execute()
                    .body();

            // 解析关键信息返回给 AI
            return parseWeatherInfo(city, jsonResponse);
        } catch (Exception e) {
            return "查询 " + city + " 天气失败：" + e.getMessage();
        }
    }

    /**
     * 从 wttr.in JSON 响应中提取关键天气信息。
     */
    private String parseWeatherInfo(String city, String jsonResponse) {
        try {
            cn.hutool.json.JSONObject json = cn.hutool.json.JSONUtil.parseObj(jsonResponse);
            cn.hutool.json.JSONArray currentArr = json.getJSONArray("current_condition");
            cn.hutool.json.JSONObject current = currentArr.getJSONObject(0);

            String temp = current.getStr("temp_C");
            String feelsLike = current.getStr("FeelsLikeC");
            String humidity = current.getStr("humidity");
            String weatherDesc = current.getJSONArray("lang_zh").getJSONObject(0).getStr("value");
            String windSpeed = current.getStr("windspeedKmph");
            String windDir = current.getStr("winddir16Point");
            String visibility = current.getStr("visibility");
            String uvIndex = current.getStr("uvIndex");

            return String.format(
                    "【%s 当前天气】\n天气：%s\n温度：%s°C（体感 %s°C）\n湿度：%s%%\n风向风速：%s %s km/h\n能见度：%s km\n紫外线指数：%s",
                    city, weatherDesc, temp, feelsLike, humidity, windDir, windSpeed, visibility, uvIndex);
        } catch (Exception e) {
            return city + " 天气数据解析失败，原始响应：" + jsonResponse.substring(0, Math.min(200, jsonResponse.length()));
        }
    }
}
