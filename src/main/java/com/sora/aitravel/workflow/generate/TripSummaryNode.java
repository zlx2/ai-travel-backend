package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.dto.model.FoodSpotDTO;
import com.sora.aitravel.dto.model.HotelAreaDTO;
import com.sora.aitravel.dto.model.RecommendationContextDTO;
import com.sora.aitravel.dto.model.ScenicSpotDTO;
import com.sora.aitravel.dto.model.TransportPlanDTO;
import com.sora.aitravel.dto.model.TravelModeDTO;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 所有天数生成后，组装行程总览所需上下文。 */
@Slf4j
@Component
public class TripSummaryNode {

    public void execute(GenerateWorkflowContext context) {
        CityProfile profile = context.getCityProfile();
        TravelModeDTO travelMode =
                new TravelModeDTO(
                        "TAXI",
                        false,
                        "Generate V1 先按城市内短途交通模拟；后续接入高德路线工具后再精确判断。",
                        List.of("当前交通时间为模拟估算，真实接入后以路线工具为准。"));

        context.setRecommendationContext(
                new RecommendationContextDTO(
                        profile.scenicCandidates().stream().map(this::toScenicSpot).toList(),
                        profile.foodCandidates().stream().map(this::toFoodSpot).toList(),
                        profile.hotelCandidates().stream().map(this::toHotelArea).toList(),
                        new TransportPlanDTO(travelMode, null, null, travelMode.tips())));
        context.setRecommendationPromptContext("SIMULATED_TOOL_DATA_BASED_GENERATION");
        log.info("节点[trip-summary]：已生成完整行程总览上下文，lockedDays={}", context.getLockedDailyPlans().size());
    }

    private ScenicSpotDTO toScenicSpot(PoiCandidate candidate) {
        return new ScenicSpotDTO(candidate.name(), candidate.area(), candidate.reason(), "2小时", false);
    }

    private FoodSpotDTO toFoodSpot(PoiCandidate candidate) {
        return new FoodSpotDTO(candidate.name(), candidate.area(), "本地美食", candidate.reason());
    }

    private HotelAreaDTO toHotelArea(PoiCandidate candidate) {
        return new HotelAreaDTO(candidate.name(), candidate.reason(), "价格以实际预订平台为准");
    }
}
