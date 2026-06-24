package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.dto.model.FoodSpotDTO;
import com.sora.aitravel.dto.model.HotelAreaDTO;
import com.sora.aitravel.dto.model.RecommendationContextDTO;
import com.sora.aitravel.dto.model.ScenicSpotDTO;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 推荐上下文提示词构建节点。
 *
 * <p>将结构化的景点、美食、住宿和交通建议转换为模型可读文本。当前假数据生成节点也会使用该文本， 后续接入真实模型时可直接放进 TripPlanGenerateNode 的 prompt。
 */
@Component
public class RecommendationPromptBuildNode {
    public void execute(GenerateWorkflowContext context) {
        RecommendationContextDTO recommendation = context.getRecommendationContext();
        String scenic =
                recommendation.getScenicSpots().stream()
                        .map(ScenicSpotDTO::getName)
                        .collect(Collectors.joining("、"));
        String food =
                recommendation.getFoodSpots().stream()
                        .map(FoodSpotDTO::getName)
                        .collect(Collectors.joining("、"));
        String hotel =
                recommendation.getHotelAreas().stream()
                        .map(HotelAreaDTO::getArea)
                        .collect(Collectors.joining("、"));

        context.setRecommendationPromptContext(
                """
                推荐上下文：
                - 景点候选：%s
                - 美食候选：%s
                - 住宿区域：%s
                - 交通方式：%s，原因：%s
                """
                        .formatted(
                                scenic,
                                food,
                                hotel,
                                recommendation.getTransportPlan().getTravelMode().getMode(),
                                recommendation.getTransportPlan().getTravelMode().getReason()));
    }
}
