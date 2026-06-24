package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.dto.model.HotelAreaDTO;
import com.sora.aitravel.dto.model.RecommendationContextDTO;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 住宿区域推荐占位节点。
 *
 * <p>当前只推荐住宿区域和预算范围，不做酒店库存、房价和预订。后续可替换为高德酒店 POI 与业务评分逻辑。
 */
@Component
public class MockHotelAreaRecommendNode {
    public void execute(GenerateWorkflowContext context) {
        String destination = displayDestination(context.getRequest().requirement());
        RecommendationContextDTO current = context.getRecommendationContext();

        List<HotelAreaDTO> hotelAreas =
                List.of(
                        new HotelAreaDTO(
                                destination + "核心商圈", "餐饮、交通和夜间活动选择丰富，适合首次到访。", "300-600元/晚"),
                        new HotelAreaDTO(
                                destination + "交通枢纽附近", "适合早到晚走或需要衔接周边自驾路线的行程。", "220-450元/晚"),
                        new HotelAreaDTO(
                                destination + "老城慢游区域", "适合偏文化体验和慢节奏旅行的用户。", "260-520元/晚"));

        context.setRecommendationContext(
                new RecommendationContextDTO(
                        current.scenicSpots(),
                        current.foodSpots(),
                        hotelAreas,
                        current.transportPlan()));
    }

    private String displayDestination(
            com.sora.aitravel.dto.model.TravelRequirementDTO requirement) {
        if (requirement.destination() != null && !requirement.destination().isBlank()) {
            return requirement.destination();
        }
        if (requirement.routeRegion() != null && !requirement.routeRegion().isBlank()) {
            return requirement.routeRegion();
        }
        if (requirement.routeCities() != null && !requirement.routeCities().isEmpty()) {
            return String.join("-", requirement.routeCities());
        }
        return requirement.departure();
    }
}
