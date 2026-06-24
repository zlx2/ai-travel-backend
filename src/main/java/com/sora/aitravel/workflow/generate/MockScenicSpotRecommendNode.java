package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.dto.model.RecommendationContextDTO;
import com.sora.aitravel.dto.model.ScenicSpotDTO;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 景点推荐占位节点。
 *
 * <p>当前生成假数据以跑通正式流程；后续由景点推荐同学替换为数据库、高德 POI 和 AI 评估逻辑。
 */
@Component
public class MockScenicSpotRecommendNode {
    public void execute(GenerateWorkflowContext context) {
        String destination = displayDestination(context.getRequest().getRequirement());
        RecommendationContextDTO current = context.getRecommendationContext();

        List<ScenicSpotDTO> scenicSpots =
                List.of(
                        new ScenicSpotDTO(
                                destination + "城市地标",
                                "核心城区",
                                "适合作为初到目的地的第一站，方便建立城市印象。",
                                "2小时",
                                false),
                        new ScenicSpotDTO(
                                destination + "历史文化街区", "老城区域", "适合安排慢节奏步行和拍照体验。", "3小时", false),
                        new ScenicSpotDTO(
                                destination + "周边自然景区", "城市周边", "适合自驾或包车串联，作为周边游亮点。", "半天", true));

        context.setRecommendationContext(
                new RecommendationContextDTO(
                        scenicSpots,
                        current.getFoodSpots(),
                        current.getHotelAreas(),
                        current.getTransportPlan()));
    }

    private String displayDestination(
            com.sora.aitravel.dto.model.TravelRequirementDTO requirement) {
        if (requirement.getDestination() != null && !requirement.getDestination().isBlank()) {
            return requirement.getDestination();
        }
        if (requirement.getRouteRegion() != null && !requirement.getRouteRegion().isBlank()) {
            return requirement.getRouteRegion();
        }
        if (requirement.getRouteCities() != null && !requirement.getRouteCities().isEmpty()) {
            return String.join("-", requirement.getRouteCities());
        }
        return requirement.getDeparture();
    }
}
