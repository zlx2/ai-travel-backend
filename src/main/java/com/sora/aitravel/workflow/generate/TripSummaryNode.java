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

/** 汇总候选数据，形成生成响应中的 recommendationContext。 */
@Slf4j
@Component
public class TripSummaryNode {

    public void execute(GenerateWorkflowContext context) {
        CityProfile profile = context.getCityProfile();
        TravelModeDTO travelMode =
                new TravelModeDTO(
                        "FRONTEND_AMAP",
                        true,
                        "路线里程、耗时、附近搜索和导航由前端调用高德地图完成。",
                        List.of("后端只负责给出景点顺序和推荐交通方式。"));
        context.setRecommendationContext(
                new RecommendationContextDTO(
                        profile.scenicCandidates().stream().map(this::toScenicSpot).toList(),
                        profile.foodCandidates().stream().map(this::toFoodSpot).toList(),
                        profile.hotelCandidates().stream().map(this::toHotelArea).toList(),
                        new TransportPlanDTO(travelMode, null, null, travelMode.getTips())));
        log.info(
                "节点[trip-summary]：已生成候选数据摘要，lockedDays={}",
                context.getLockedDailyPlans().size());
    }

    private ScenicSpotDTO toScenicSpot(PoiCandidate candidate) {
        return new ScenicSpotDTO(
                candidate.getName(), candidate.getArea(), candidate.getReason(), "约2小时", false);
    }

    private FoodSpotDTO toFoodSpot(PoiCandidate candidate) {
        return new FoodSpotDTO(
                candidate.getName(), candidate.getArea(), "本地美食", candidate.getReason());
    }

    private HotelAreaDTO toHotelArea(PoiCandidate candidate) {
        return new HotelAreaDTO(candidate.getName(), candidate.getReason(), "价格以实际预订平台为准");
    }
}
