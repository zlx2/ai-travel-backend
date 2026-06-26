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
                candidate.getName(), candidate.getArea(), scenicSummary(candidate), "约2小时", false);
    }

    private FoodSpotDTO toFoodSpot(PoiCandidate candidate) {
        return new FoodSpotDTO(candidate.getName(), candidate.getArea(), null, null);
    }

    private HotelAreaDTO toHotelArea(PoiCandidate candidate) {
        return new HotelAreaDTO(candidate.getName(), null, null);
    }

    private String scenicSummary(PoiCandidate candidate) {
        String name = candidate.getName() == null ? "该景点" : candidate.getName();
        if (name.contains("博物馆") || name.contains("博物院") || name.contains("纪念馆")) {
            return "以展陈和主题收藏为主，适合了解当地故事。";
        }
        if (name.contains("山") || name.contains("风景区") || name.contains("湿地")) {
            return "以自然景观和步道游览为主，适合安排慢行。";
        }
        if (name.contains("古街") || name.contains("街区") || name.contains("古镇")) {
            return "以街巷、建筑和沿途小店为主，适合边走边看。";
        }
        return "可作为候选景点，具体看点以现场实际信息为准。";
    }
}
