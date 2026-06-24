package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.tools.HotelTool;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 酒店数据获取节点。
 *
 * <p>调用 {@link HotelTool} 获取目的地酒店信息，将结果存入工作流上下文供后续节点使用。
 */
@Slf4j
@Component
public class HotelFetchNode {

    private final HotelTool hotelTool;

    public HotelFetchNode(HotelTool hotelTool) {
        this.hotelTool = hotelTool;
    }

    /**
     * 执行酒店数据获取。
     *
     * <p>根据用户需求中的目的地、出行日期和天数，调用 HotelTool 查询酒店信息。
     */
    public void execute(GenerateWorkflowContext context) {
        TravelRequirementDTO requirement = context.getRequirement();
        String destination = requirement.destination();

        if (destination == null || destination.isBlank()) {
            log.warn("节点[hotel-fetch]：目的地为空，跳过酒店查询");
            return;
        }

        // 计算入住/离店日期
        String travelDate = requirement.travelDate();
        LocalDate checkIn = travelDate != null ? LocalDate.parse(travelDate) : LocalDate.now().plusDays(1);
        LocalDate checkOut = checkIn.plusDays(requirement.days() != null ? requirement.days() : 3);
        String checkInStr = checkIn.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String checkOutStr = checkOut.format(DateTimeFormatter.ISO_LOCAL_DATE);

        try {
            log.info("节点[hotel-fetch]：调用 HotelTool 查询 {} 酒店，入住={}，离店={}", destination, checkInStr, checkOutStr);
            String hotelData = hotelTool.searchHotel(destination, checkInStr, checkOutStr);
            context.setHotelSearchResult(hotelData);
            log.info("节点[hotel-fetch]：酒店数据获取成功，长度={}", hotelData.length());
        } catch (Exception e) {
            log.error("节点[hotel-fetch]：酒店查询失败，destination={}", destination, e);
            context.setHotelSearchResult("酒店数据暂不可用：" + e.getMessage());
        }
    }
}
