package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.common.enums.RentalStoreUsageEnum;
import com.sora.aitravel.dto.model.RecommendationContextDTO;
import com.sora.aitravel.dto.model.RentalStoreDTO;
import com.sora.aitravel.dto.model.TransportPlanDTO;
import com.sora.aitravel.dto.model.TravelModeDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.service.RentalStoreService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 交通推荐节点。
 *
 * <p>当前只做最小闭环：公共交通给提示，自驾则尝试解析推荐取还车点。解析失败不会阻断行程生成， 后续可接入高德路线规划、距离计算和停车提示。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransportRecommendNode {

    private final RentalStoreService rentalStoreService;

    public void execute(GenerateWorkflowContext context) {
        RecommendationContextDTO current = context.getRecommendationContext();
        TravelModeDTO travelMode = current.getTransportPlan().getTravelMode();
        List<String> tips = new ArrayList<>(current.getTransportPlan().getTips());
        RentalStoreDTO pickupStore = null;
        RentalStoreDTO returnStore = null;

        if (Boolean.TRUE.equals(travelMode.getRecommended())
                && "SELF_DRIVE".equals(travelMode.getMode())) {
            TravelRequirementDTO requirement = context.getRequest().getRequirement();
            String targetName =
                    firstNonBlank(requirement.getDeparture(), requirement.getDestination());
            String cityName = requirement.getDestination();
            try {
                pickupStore =
                        rentalStoreService.resolveRentalStore(
                                targetName, cityName, RentalStoreUsageEnum.PICKUP);
                returnStore =
                        rentalStoreService.resolveRentalStore(
                                targetName, cityName, RentalStoreUsageEnum.RETURN);
                tips.add("已根据目的地附近地图信息补充推荐取还车点，实际可用车辆以下单平台为准。");
            } catch (Exception e) {
                log.warn("自驾取还车点解析失败，继续使用无租车点的交通建议", e);
                tips.add("暂未解析到推荐取还车点，可先按自驾路线生成行程，后续再补充租车点。");
            }
        }

        context.setRecommendationContext(
                new RecommendationContextDTO(
                        current.getScenicSpots(),
                        current.getFoodSpots(),
                        current.getHotelAreas(),
                        new TransportPlanDTO(travelMode, pickupStore, returnStore, tips)));
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }
}
