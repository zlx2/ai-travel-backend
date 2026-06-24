package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.dto.model.FoodSpotDTO;
import com.sora.aitravel.dto.model.HotelAreaDTO;
import com.sora.aitravel.dto.model.RecommendationContextDTO;
import com.sora.aitravel.dto.model.RentalQuoteOptionDTO;
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
 *
 * <p>实现 {@link WorkflowNode} 接口，是 {@link TripGenerateWorkflow} 工作流的第五个（最后）步骤。 负责将从 AI 模型获取的原始 JSON
 * 行程数据（经校验/修复后）转换为标准化的 {@link com.sora.aitravel.dto.response.TripGenerateResponse} 结构，
 * 包括构建每日活动列表、景点详情等，供 Controller 层返回给前端。
 *
 * <p>在整个工作流中的位置：生成流程第 5 步（最后执行）。
 *
 * <p>输入：{@link GenerateWorkflowContext#rawModelResponse}（校验/修复后的 JSON）。 输出：格式化的最终响应写入 {@link
 * GenerateWorkflowContext#result}。
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
        RentalQuoteOptionDTO selectedQuote = context.getRequest().selectedQuote();
        TripPlanDTO tripPlan = buildMockTripPlan(requirement, recommendation, selectedQuote);
        context.setResult(
                new TripGenerateResponse(
                        context.getRequest().conversationId(),
                        requirement,
                        selectedQuote,
                        recommendation,
                        tripPlan));
    }

    private TripPlanDTO buildMockTripPlan(
            TravelRequirementDTO requirement,
            RecommendationContextDTO recommendation,
            RentalQuoteOptionDTO selectedQuote) {
        if ("ROAD_TRIP".equals(requirement.routeMode())) {
            return buildRoadTripPlan(requirement, recommendation, selectedQuote);
        }
        if ("LANDING_RENTAL_TRIP".equals(requirement.routeMode())) {
            return buildLandingRentalPlan(requirement, recommendation, selectedQuote);
        }
        return buildDestinationCityPlan(requirement, recommendation);
    }

    private TripPlanDTO buildDestinationCityPlan(
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
                            requirement.destination(),
                            requirement.destination(),
                            requirement.destination(),
                            null,
                            null,
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

    private TripPlanDTO buildLandingRentalPlan(
            TravelRequirementDTO requirement,
            RecommendationContextDTO recommendation,
            RentalQuoteOptionDTO quote) {
        List<TripPlanDTO.DailyPlan> dailyPlans = new ArrayList<>();
        List<ScenicSpotDTO> scenicSpots = recommendation.scenicSpots();
        List<FoodSpotDTO> foodSpots = recommendation.foodSpots();
        String destination = requirement.destination();
        for (int day = 1; day <= requirement.days(); day++) {
            List<TripPlanDTO.PlanItem> items = new ArrayList<>();
            if (day == 1) {
                items.add(
                        new TripPlanDTO.PlanItem(
                                "10:30",
                                quote == null || quote.pickupPoiName() == null
                                        ? destination + "取车点"
                                        : quote.pickupPoiName(),
                                "到达后办理取车，核对车辆外观、油/电量、保险和押金信息。",
                                "45分钟",
                                "RENTAL_CAR",
                                0,
                                "取车点来自系统报价，不重新估价。"));
            }
            ScenicSpotDTO scenic = scenicSpots.get((day - 1) % scenicSpots.size());
            FoodSpotDTO food = foodSpots.get((day - 1) % foodSpots.size());
            items.add(
                    new TripPlanDTO.PlanItem(
                            day == 1 ? "14:00" : "09:30",
                            scenic.name(),
                            scenic.reason(),
                            scenic.suggestedDuration(),
                            "RENTAL_CAR",
                            100,
                            "当天只安排目的地及周边一组主要动线，避免频繁折返。"));
            if (day == requirement.days()) {
                items.add(
                        new TripPlanDTO.PlanItem(
                                "17:00",
                                quote == null || quote.returnPoiName() == null
                                        ? destination + "还车点"
                                        : quote.returnPoiName(),
                                "预留还车检查和交通衔接时间。",
                                "45分钟",
                                "RENTAL_CAR",
                                0,
                                "还车点来自系统报价。"));
            }
            dailyPlans.add(
                    new TripPlanDTO.DailyPlan(
                            day,
                            "第 " + day + " 天：落地租车游",
                            destination,
                            destination,
                            destination,
                            day == 1 ? 1.0 : 1.5,
                            day == 1 ? 30 : 80,
                            items,
                            List.of(food.name() + "：" + food.specialty()),
                            260,
                            List.of("市区停车压力较大，热门商圈建议提前查停车场。")));
        }

        int rentalCost = quote == null ? 0 : quote.feeBreakdown().totalPriceCent() / 100;
        int hotelCost = 350 * requirement.days();
        int foodCost = 160 * requirement.days();
        int ticketCost = 160 * requirement.days();
        HotelAreaDTO hotelArea = recommendation.hotelAreas().get(0);
        List<String> tips = new ArrayList<>(recommendation.transportPlan().tips());
        tips.add("租车价格以 selectedQuote 系统报价为准，行程生成不会重新编价。");

        return new TripPlanDTO(
                destination + requirement.days() + "日落地租车方案",
                destination,
                requirement.days(),
                "到达目的地后取车，串联市区与周边景点，并在最后一天预留还车时间。",
                dailyPlans,
                new TripPlanDTO.BudgetSummary(
                        rentalCost + hotelCost + foodCost + ticketCost,
                        rentalCost,
                        foodCost,
                        ticketCost,
                        hotelCost,
                        "交通费用仅使用系统 selectedQuote 报价，押金不计入总价。"),
                new TripPlanDTO.AccommodationSuggestion(
                        hotelArea.area(), hotelArea.reason(), hotelArea.priceRange()),
                tips);
    }

    private TripPlanDTO buildRoadTripPlan(
            TravelRequirementDTO requirement,
            RecommendationContextDTO recommendation,
            RentalQuoteOptionDTO selectedQuote) {
        List<String> routeCities = normalizeRouteCities(requirement);
        if (routeCities.size() < 2) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "自驾路线至少需要两个城市或节点");
        }
        int majorDriveLegs = routeCities.size() - 1;
        if (majorDriveLegs > requirement.days()) {
            throw new BusinessException(ErrorCode.CONFLICT, "行程天数过少，无法为每段跨城市驾驶预留独立日期");
        }

        List<TripPlanDTO.DailyPlan> dailyPlans = new ArrayList<>();
        List<FoodSpotDTO> foodSpots = recommendation.foodSpots();
        List<String> conflictTips = new ArrayList<>();
        for (int day = 1; day <= requirement.days(); day++) {
            int legIndex = Math.min(day - 1, majorDriveLegs - 1);
            boolean drivingDay = day <= majorDriveLegs;
            String fromCity =
                    drivingDay
                            ? routeCities.get(legIndex)
                            : routeCities.get(routeCities.size() - 1);
            String toCity = drivingDay ? routeCities.get(legIndex + 1) : fromCity;
            double driveHours = drivingDay ? estimateDriveHours(fromCity, toCity) : 0.8;
            int distance = drivingDay ? estimateDriveDistance(driveHours) : 35;
            if (driveHours > 7.0) {
                conflictTips.add("第 " + day + " 天预计驾驶超过 7 小时，建议拆分路线或增加天数。");
            }

            FoodSpotDTO food = foodSpots.get((day - 1) % foodSpots.size());
            dailyPlans.add(
                    new TripPlanDTO.DailyPlan(
                            day,
                            drivingDay ? fromCity + " -> " + toCity : toCity + "城市休整",
                            fromCity,
                            toCity,
                            toCity,
                            driveHours,
                            distance,
                            roadTripItems(day, fromCity, toCity, drivingDay, driveHours),
                            List.of(toCity + "本地餐饮：" + food.specialty()),
                            drivingDay ? 360 : 260,
                            roadTripDayTips(driveHours)));
        }

        int rentalCost =
                selectedQuote == null ? 0 : selectedQuote.feeBreakdown().totalPriceCent() / 100;
        int hotelCost = 320 * requirement.days();
        int foodCost = 150 * requirement.days();
        int ticketCost = 150 * requirement.days();
        List<String> tips = new ArrayList<>(recommendation.transportPlan().tips());
        tips.add("每天最多安排一段主要长距离驾驶，长途驾驶日减少景点密度。");
        tips.add("租车价格以 selectedQuote 系统报价为准，押金不计入总价。");
        tips.addAll(conflictTips);

        return new TripPlanDTO(
                routeCities.get(0) + "出发" + requirement.days() + "日自驾路线",
                String.join("-", routeCities),
                requirement.days(),
                "先形成跨城市路线骨架，再按每日一段主要驾驶组织游玩与住宿。",
                dailyPlans,
                new TripPlanDTO.BudgetSummary(
                        rentalCost + hotelCost + foodCost + ticketCost,
                        rentalCost,
                        foodCost,
                        ticketCost,
                        hotelCost,
                        "交通费用仅使用系统 selectedQuote 报价，油电费和高速费需按实际发生另计。"),
                new TripPlanDTO.AccommodationSuggestion(
                        "当日 overnightCity", "自驾游按每日抵达城市住宿，减少夜间赶路。", "280-520元/晚"),
                tips);
    }

    private List<TripPlanDTO.PlanItem> roadTripItems(
            int day, String fromCity, String toCity, boolean drivingDay, double driveHours) {
        if (!drivingDay) {
            return List.of(
                    new TripPlanDTO.PlanItem(
                            "10:00",
                            toCity + "城区",
                            "城市休整、补给和自由探索。",
                            "3小时",
                            "SELF_DRIVE",
                            100,
                            "休整日降低驾驶强度。"));
        }
        return List.of(
                new TripPlanDTO.PlanItem(
                        "09:00",
                        fromCity + "出发",
                        "完成车辆检查后开始当天主要驾驶段。",
                        formatHours(driveHours),
                        "SELF_DRIVE",
                        0,
                        driveHours > 4 ? "轻松节奏下该驾驶时长偏长，建议中途休息。" : "当天驾驶强度适中。"),
                new TripPlanDTO.PlanItem(
                        "15:00",
                        toCity + "核心区域",
                        "抵达后安排低强度游览和晚餐。",
                        "2-3小时",
                        "SELF_DRIVE",
                        120,
                        "抵达后优先办理入住，避免疲劳驾驶。"));
    }

    private List<String> roadTripDayTips(double driveHours) {
        List<String> tips = new ArrayList<>();
        tips.add("提前确认停车场、充电/加油补给点和高速路况。");
        if (driveHours > 4.0) {
            tips.add("轻松节奏下单日驾驶建议不超过 4 小时，本日需增加休息。");
        }
        if (driveHours > 7.0) {
            tips.add("单日驾驶超过 7 小时，建议拆分路线。");
        }
        return tips;
    }

    private List<String> normalizeRouteCities(TravelRequirementDTO requirement) {
        List<String> route = new ArrayList<>();
        if (requirement.routeCities() != null) {
            route.addAll(requirement.routeCities());
        }
        if (route.isEmpty()) {
            route.add(requirement.departure());
            if (requirement.destination() != null && !requirement.destination().isBlank()) {
                route.add(requirement.destination());
            }
        }
        if (!route.get(0).equals(requirement.departure())) {
            route.add(0, requirement.departure());
        }
        if ("LOOP".equals(requirement.routeStructure())
                && !route.get(route.size() - 1).equals(requirement.departure())) {
            route.add(requirement.departure());
        }
        return route;
    }

    private double estimateDriveHours(String fromCity, String toCity) {
        if (fromCity.equals(toCity)) {
            return 0.8;
        }
        int hash = Math.abs((fromCity + toCity).hashCode());
        return Math.round((2.0 + (hash % 36) / 10.0) * 10.0) / 10.0;
    }

    private int estimateDriveDistance(double hours) {
        return (int) Math.round(hours * 82);
    }

    private String formatHours(double hours) {
        return hours + "小时";
    }
}
