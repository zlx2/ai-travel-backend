package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.dto.model.FoodSpotDTO;
import com.sora.aitravel.dto.model.HotelAreaDTO;
import com.sora.aitravel.dto.model.RecommendationContextDTO;
import com.sora.aitravel.dto.model.ScenicSpotDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.model.TripPlanDTO;
import com.sora.aitravel.dto.response.TripGenerateResponse;
import com.sora.aitravel.workflow.WorkflowNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 生成结果合并节点。
 * <p>
 * 实现 {@link WorkflowNode} 接口，是 {@link TripGenerateWorkflow} 工作流的第五个（最后）步骤。
 * 负责将从 AI 模型获取的原始 JSON 行程数据（经校验/修复后）转换为标准化的
 * {@link com.sora.aitravel.dto.response.TripGenerateResponse} 结构，
 * 包括构建每日活动列表、景点详情等，供 Controller 层返回给前端。
 * <p>
 * 在整个工作流中的位置：生成流程第 5 步（最后执行）。
 * <p>
 * 输入：{@link GenerateWorkflowContext#rawModelResponse}（校验/修复后的 JSON）。
 * 输出：格式化的最终响应写入 {@link GenerateWorkflowContext#result}。
 */
@Component
public class GenerateResultMergeNode implements WorkflowNode<GenerateWorkflowContext> {

    /**
     * 执行结果合并逻辑——将模型输出转换为标准响应结构。
     *
     * @param context 工作流上下文，读取模型响应 JSON 并构建最终结果
     */
    public void execute(GenerateWorkflowContext context) {
        TravelRequirementDTO requirement = context.getRequest().requirement();
        RecommendationContextDTO recommendation = context.getRecommendationContext();
        TripPlanDTO tripPlan = buildMockTripPlan(requirement, recommendation);
        context.setResult(
                new TripGenerateResponse(
                        context.getRequest().conversationId(),
                        requirement,
                        recommendation,
                        tripPlan));
    }

    private TripPlanDTO buildMockTripPlan(
            TravelRequirementDTO requirement, RecommendationContextDTO recommendation) {
        List<TripPlanDTO.DailyPlan> dailyPlans = new ArrayList<>();
        List<ScenicSpotDTO> scenicSpots = recommendation.scenicSpots();
        List<FoodSpotDTO> foodSpots = recommendation.foodSpots();

        for (int day = 1; day <= requirement.days(); day++) {
            ScenicSpotDTO morning = scenicSpots.get((day - 1) % scenicSpots.size());
            ScenicSpotDTO afternoon = scenicSpots.get(day % scenicSpots.size());
            FoodSpotDTO food = foodSpots.get((day - 1) % foodSpots.size());

            dailyPlans.add(
                    new TripPlanDTO.DailyPlan(
                            day,
                            "第 " + day + " 天：" + morning.area() + "深度体验",
                            List.of(
                                    new TripPlanDTO.PlanItem(
                                            "09:30",
                                            morning.name(),
                                            morning.reason(),
                                            morning.suggestedDuration(),
                                            recommendation.transportPlan().travelMode().mode(),
                                            80,
                                            "当前为假数据流程，后续会替换为真实景点 POI。"),
                                    new TripPlanDTO.PlanItem(
                                            "14:30",
                                            afternoon.name(),
                                            afternoon.reason(),
                                            afternoon.suggestedDuration(),
                                            recommendation.transportPlan().travelMode().mode(),
                                            100,
                                            "根据上午区域就近安排，减少绕路。")),
                            List.of(food.name() + "：" + food.specialty()),
                            260,
                            List.of(food.reason())));
        }

        HotelAreaDTO hotelArea = recommendation.hotelAreas().get(0);
        int hotelCost = 350 * requirement.days();
        int foodCost = 160 * requirement.days();
        int ticketCost = 180 * requirement.days();
        int transportCost =
                "SELF_DRIVE".equals(recommendation.transportPlan().travelMode().mode())
                        ? 260 * requirement.days()
                        : 80 * requirement.days();

        return new TripPlanDTO(
                requirement.destination() + requirement.days() + "日旅行方案",
                requirement.destination(),
                requirement.days(),
                "基于景点、美食、住宿区域和交通方式推荐上下文生成的最小可跑通方案。",
                dailyPlans,
                new TripPlanDTO.BudgetSummary(
                        hotelCost + foodCost + ticketCost + transportCost,
                        transportCost,
                        foodCost,
                        ticketCost,
                        hotelCost,
                        "当前为流程跑通用估算，后续会接入真实推荐和价格规则。"),
                new TripPlanDTO.AccommodationSuggestion(
                        hotelArea.area(), hotelArea.reason(), hotelArea.priceRange()),
                recommendation.transportPlan().tips());
    }
}
