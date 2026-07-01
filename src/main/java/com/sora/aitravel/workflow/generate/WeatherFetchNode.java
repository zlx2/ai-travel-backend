package com.sora.aitravel.workflow.generate;

import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.REQUIREMENT;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.WEATHER_FORECAST;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.tools.WeatherTool;
import com.sora.aitravel.workflow.generate.state.TripGraphStateCodec;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 天气数据获取节点。
 *
 * <p>调用 {@link WeatherTool} 获取目的地天气预报，将结果存入工作流上下文供后续节点使用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherFetchNode {

    private final WeatherTool weatherTool;


    public Map<String, Object> execute(OverAllState state) {
        TravelRequirementDTO requirement = TripGraphStateCodec.required(state, REQUIREMENT, TravelRequirementDTO.class);
        return TripGraphStateCodec.patch(WEATHER_FORECAST, fetchWeather(requirement));
    }

    private String fetchWeather(TravelRequirementDTO requirement) {
        String destination = primarySearchCity(requirement);
        if (destination == null || destination.isBlank()) {
            log.warn("节点[weather-fetch]：目的地为空，跳过天气查询");
            return null;
        }

        try {
            log.info("节点[weather-fetch]：调用 WeatherTool 查询 {} 天气", destination);
            String weatherData = weatherTool.getWeather(destination);
            log.info("节点[weather-fetch]：天气数据获取成功，长度={}", weatherData.length());
            return weatherData;
        } catch (Exception e) {
            log.error("节点[weather-fetch]：天气查询失败，destination={}", destination, e);
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
}
