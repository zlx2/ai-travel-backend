package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.tools.WeatherTool;
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

    public void execute(GenerateWorkflowContext context) {
        TravelRequirementDTO requirement = context.getRequirement();
        String destination = requirement.getDestination();

        if (destination == null || destination.isBlank()) {
            log.warn("节点[weather-fetch]：目的地为空，跳过天气查询");
            return;
        }

        try {
            log.info("节点[weather-fetch]：调用 WeatherTool 查询 {} 天气", destination);
            String weatherData = weatherTool.getWeather(destination);
            context.setWeatherForecast(weatherData);
            log.info("节点[weather-fetch]：天气数据获取成功，长度={}", weatherData.length());
        } catch (Exception e) {
            log.error("节点[weather-fetch]：天气查询失败，destination={}", destination, e);
            context.setWeatherForecast("天气数据暂不可用：" + e.getMessage());
        }
    }
}
