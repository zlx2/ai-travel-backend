package com.sora.aitravel.dto.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 行程生成推荐上下文。
 *
 * <p>这是 TripGenerateWorkflow 在调用大模型前准备的结构化资料包，包含景点、美食、住宿区域和交通建议。 当前节点可先填充假数据，后续逐步替换为高德、数据库和 AI
 * 评估结果。
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
