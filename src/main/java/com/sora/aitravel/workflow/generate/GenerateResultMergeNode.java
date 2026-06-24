package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.dto.model.FoodSpotDTO;
import com.sora.aitravel.dto.model.HotelAreaDTO;
import com.sora.aitravel.dto.model.RecommendationContextDTO;
import com.sora.aitravel.dto.model.ScenicSpotDTO;
import com.sora.aitravel.dto.model.TransportPlanDTO;
import com.sora.aitravel.dto.model.TravelModeDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.model.TripPlanDTO;
import com.sora.aitravel.dto.response.TripGenerateResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 将 Generate 各节点产物组装为前端可用响应。 */
@Slf4j
@Component
public class GenerateResultMergeNode {

    public void execute(GenerateWorkflowContext context) {
        TravelRequirementDTO requirement = context.getRequirement();
        RecommendationContextDTO recommendationContext =
                normalizeRecommendationContext(context.getRecommendationContext());
        context.setRecommendationContext(recommendationContext);
        TripPlanDTO tripPlan = buildTripPlan(context, requirement);
        context.setResult(
                new TripGenerateResponse(
                        context.getRequest().conversationId(),
                        requirement,
                        context.getSelectedQuote(),
                        recommendationContext,
                        tripPlan));
        log.info("节点[result-merge]：Generate 响应已组装，dailyPlans={}", tripPlan.dailyPlans().size());
    }

    private RecommendationContextDTO normalizeRecommendationContext(
            RecommendationContextDTO recommendationContext) {
        if (recommendationContext == null) {
            return null;
        }
        return new RecommendationContextDTO(
                normalizeScenicSpots(recommendationContext.scenicSpots()),
                normalizeFoodSpots(recommendationContext.foodSpots()),
                normalizeHotelAreas(recommendationContext.hotelAreas()),
                normalizeTransportPlan(recommendationContext.transportPlan()));
    }

    private List<ScenicSpotDTO> normalizeScenicSpots(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(this::toScenicSpot).toList();
    }

    private ScenicSpotDTO toScenicSpot(Object value) {
        if (value instanceof ScenicSpotDTO dto) {
            return dto;
        }
        Map<?, ?> map = asMap(value);
        return new ScenicSpotDTO(
                text(map, "name"),
                text(map, "area"),
                text(map, "reason"),
                text(map, "suggestedDuration"),
                bool(map, "suitableForSelfDrive"));
    }

    private List<FoodSpotDTO> normalizeFoodSpots(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(this::toFoodSpot).toList();
    }

    private FoodSpotDTO toFoodSpot(Object value) {
        if (value instanceof FoodSpotDTO dto) {
            return dto;
        }
        Map<?, ?> map = asMap(value);
        return new FoodSpotDTO(
                text(map, "name"), text(map, "area"), text(map, "specialty"), text(map, "reason"));
    }

    private List<HotelAreaDTO> normalizeHotelAreas(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(this::toHotelArea).toList();
    }

    private HotelAreaDTO toHotelArea(Object value) {
        if (value instanceof HotelAreaDTO dto) {
            return dto;
        }
        Map<?, ?> map = asMap(value);
        return new HotelAreaDTO(text(map, "area"), text(map, "reason"), text(map, "priceRange"));
    }

    private TransportPlanDTO normalizeTransportPlan(Object value) {
        if (value instanceof TransportPlanDTO dto) {
            return dto;
        }
        Map<?, ?> map = asMap(value);
        Object travelModeValue = map.get("travelMode");
        TravelModeDTO travelMode =
                travelModeValue instanceof TravelModeDTO dto
                        ? dto
                        : toTravelMode(asMap(travelModeValue));
        List<String> tips = stringList(map.get("tips"));
        return new TransportPlanDTO(travelMode, null, null, tips);
    }

    private TravelModeDTO toTravelMode(Map<?, ?> map) {
        return new TravelModeDTO(
                text(map, "mode"),
                bool(map, "recommended"),
                text(map, "reason"),
                stringList(map.get("tips")));
    }

    private TripPlanDTO buildTripPlan(
            GenerateWorkflowContext context, TravelRequirementDTO requirement) {
        List<String> tips = new ArrayList<>();
        tips.add("本版 Generate 已按“先查数据、再生成、再校验”的流程跑通。");
        tips.add("工具查询暂未真实接入，当前地点来源标记为 SIMULATED_AMAP。");
        if (context.getSelectedQuote() != null
                && context.getSelectedQuote().priceSnapshot() != null
                && Boolean.TRUE.equals(context.getSelectedQuote().priceSnapshot().get("mock"))) {
            tips.add("租车报价为模拟数据，仅用于前后端联调。");
        }
        for (DayPlanValidationReport report : context.getDayValidationReports()) {
            if (!Boolean.TRUE.equals(report.passed())) {
                tips.add("第 " + report.day() + " 天存在校验提示：" + String.join("；", report.warnings()));
            }
        }

        int foodCost = 180 * safePeopleCount(requirement) * requirement.days();
        int transportCost =
                context.getSelectedQuote() == null
                        ? 80 * safePeopleCount(requirement) * requirement.days()
                        : context.getSelectedQuote().feeBreakdown().totalPriceCent() / 100;
        int hotelCost = 300 * Math.max(requirement.days() - 1, 1);

        return new TripPlanDTO(
                displayDestination(requirement) + requirement.days() + "日旅行方案",
                displayDestination(requirement),
                requirement.days(),
                "基于逐日骨架、模拟工具候选数据和后端校验结果组装的 Generate V1 行程。",
                context.getLockedDailyPlans(),
                new TripPlanDTO.BudgetSummary(
                        foodCost + transportCost + hotelCost,
                        transportCost,
                        foodCost,
                        null,
                        hotelCost,
                        "门票、人均、酒店价格不由 AI 编造；工具没有返回时保持为空或提示确认。"),
                buildAccommodationSuggestion(context),
                tips);
    }

    private TripPlanDTO.AccommodationSuggestion buildAccommodationSuggestion(
            GenerateWorkflowContext context) {
        if (context.getRecommendationContext() == null
                || context.getRecommendationContext().hotelAreas().isEmpty()) {
            return new TripPlanDTO.AccommodationSuggestion("待确认住宿区域", "后续接入酒店/住宿区域工具。", null);
        }
        var hotelArea = context.getRecommendationContext().hotelAreas().get(0);
        return new TripPlanDTO.AccommodationSuggestion(
                hotelArea.area(), hotelArea.reason(), hotelArea.priceRange());
    }

    private int safePeopleCount(TravelRequirementDTO requirement) {
        return requirement.peopleCount() == null ? 1 : requirement.peopleCount();
    }

    private Map<?, ?> asMap(Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    private String text(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Boolean bool(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value == null ? null : Boolean.valueOf(String.valueOf(value));
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }

    private String displayDestination(TravelRequirementDTO requirement) {
        if (requirement.destination() != null && !requirement.destination().isBlank()) {
            return requirement.destination();
        }
        if (requirement.routeRegion() != null && !requirement.routeRegion().isBlank()) {
            return requirement.routeRegion();
        }
        return String.join("-", requirement.routeCities());
    }
}
