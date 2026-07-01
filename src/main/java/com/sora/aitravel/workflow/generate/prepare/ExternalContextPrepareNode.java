package com.sora.aitravel.workflow.generate.prepare;

import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.HOTEL_SEARCH_RESULT;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.REQUIREMENT;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.WEATHER_FORECAST;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.sora.aitravel.ai.HotelTool;
import com.sora.aitravel.ai.WeatherTool;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.workflow.generate.state.TripGraphStateCodec;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalContextPrepareNode {

    private final WeatherTool weatherTool;
    private final HotelTool hotelTool;

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
            String data = weatherTool.getWeather(destination);
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
            String data = hotelTool.searchHotel(destination, checkInStr, checkOutStr);
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
}
