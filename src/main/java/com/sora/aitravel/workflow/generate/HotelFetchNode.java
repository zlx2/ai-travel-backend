package com.sora.aitravel.workflow.generate;

import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.HOTEL_SEARCH_RESULT;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.REQUIREMENT;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.tools.HotelTool;
import com.sora.aitravel.workflow.generate.state.TripGraphStateCodec;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 酒店数据获取节点。
 *
 * <p>调用 {@link HotelTool} 获取目的地酒店信息，将结果存入工作流上下文供后续节点使用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HotelFetchNode {

    private final HotelTool hotelTool;

    /**
     * 执行酒店数据获取。
     *
     * <p>根据用户需求中的目的地、出行日期和天数，调用 HotelTool 查询酒店信息。
     */
    public Map<String, Object> execute(OverAllState state) {
        TravelRequirementDTO requirement =
                TripGraphStateCodec.required(state, REQUIREMENT, TravelRequirementDTO.class);
        return TripGraphStateCodec.patch(HOTEL_SEARCH_RESULT, fetchHotels(requirement));
    }

    private String fetchHotels(TravelRequirementDTO requirement) {
        String destination = primarySearchCity(requirement);

        if (destination == null || destination.isBlank()) {
            log.warn("节点[hotel-fetch]：目的地为空，跳过酒店查询");
            return null;
        }

        // 计算入住/离店日期
        String travelDate = requirement.getTravelDate();
        LocalDate checkIn =
                travelDate != null ? LocalDate.parse(travelDate) : LocalDate.now().plusDays(1);
        LocalDate checkOut =
                checkIn.plusDays(requirement.getDays() != null ? requirement.getDays() : 3);
        String checkInStr = checkIn.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String checkOutStr = checkOut.format(DateTimeFormatter.ISO_LOCAL_DATE);

        try {
            log.info(
                    "节点[hotel-fetch]：调用 HotelTool 查询 {} 酒店，入住={}，离店={}",
                    destination,
                    checkInStr,
                    checkOutStr);
            String hotelData = hotelTool.searchHotel(destination, checkInStr, checkOutStr);
            log.info("节点[hotel-fetch]：酒店数据获取成功，长度={}", hotelData.length());
            return hotelData;
        } catch (Exception e) {
            log.error("节点[hotel-fetch]：酒店查询失败，destination={}", destination, e);
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
