package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.dto.model.FoodSpotDTO;
import com.sora.aitravel.dto.model.RecommendationContextDTO;
import com.sora.aitravel.workflow.WorkflowNode;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 美食推荐占位节点。
 *
 * <p>当前生成假数据以跑通正式流程；后续可替换为高德餐饮 POI、城市特色菜库和用户口味偏好判断。
 */
@Component
public class MockFoodRecommendNode implements WorkflowNode<GenerateWorkflowContext> {

    @Override
    public void execute(GenerateWorkflowContext context) {
        String destination = context.getRequest().requirement().destination();
        RecommendationContextDTO current = context.getRecommendationContext();

        List<FoodSpotDTO> foodSpots =
                List.of(
                        new FoodSpotDTO(
                                destination + "本地小吃集合",
                                "老城区域",
                                "地方小吃",
                                "适合穿插在步行街区和夜间行程中。"),
                        new FoodSpotDTO(
                                destination + "特色餐厅街区",
                                "核心商圈",
                                "本地菜",
                                "适合安排正餐，选择多且交通方便。"),
                        new FoodSpotDTO(
                                destination + "夜市美食区",
                                "夜间活跃区域",
                                "夜宵",
                                "适合作为晚间自由活动和体验当地生活的补充。"));

        context.setRecommendationContext(
                new RecommendationContextDTO(
                        current.scenicSpots(),
                        foodSpots,
                        current.hotelAreas(),
                        current.transportPlan()));
    }
}
