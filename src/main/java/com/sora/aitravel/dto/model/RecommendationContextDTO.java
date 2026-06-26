package com.sora.aitravel.dto.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 行程生成推荐上下文。
 *
 * <p>这是 TripGenerateWorkflow 准备的结构化资料包，包含景点、美食、住宿区域和交通建议。数据应来自真实查询或明确为空，不填充伪造候选。
 *
 * @param scenicSpots 景点候选
 * @param foodSpots 美食候选
 * @param hotelAreas 住宿区域候选
 * @param transportPlan 交通方案
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationContextDTO {

    private List<ScenicSpotDTO> scenicSpots;
    private List<FoodSpotDTO> foodSpots;
    private List<HotelAreaDTO> hotelAreas;
    private TransportPlanDTO transportPlan;
}
