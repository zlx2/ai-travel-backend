package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.dto.model.FoodSpotDTO;
import com.sora.aitravel.dto.model.HotelAreaDTO;
import com.sora.aitravel.dto.model.RecommendationContextDTO;
import com.sora.aitravel.dto.model.ScenicSpotDTO;
import com.sora.aitravel.dto.model.TransportPlanDTO;
import com.sora.aitravel.dto.model.TravelModeDTO;
import com.sora.aitravel.dto.model.TripPlanDTO;
import com.sora.aitravel.dto.response.TripGenerateResponse;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Graph 执行后把可能被转成 Map 的嵌套对象恢复成响应 DTO。 */
@Component
public class GenerateResponseNormalizer {

    public void normalize(GenerateWorkflowContext context) {
        if (context == null || context.getResult() == null) {
            return;
        }
        TripGenerateResponse result = context.getResult();
        context.setResult(
                new TripGenerateResponse(
                        result.getConversationId(),
                        result.getRequirement(),
                        result.getSelectedQuote(),
                        normalizeRecommendationContext(result.getRecommendationContext()),
                        normalizeTripPlan(result.getTripPlan())));
    }

    private RecommendationContextDTO normalizeRecommendationContext(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof RecommendationContextDTO dto) {
            return new RecommendationContextDTO(
                    normalizeScenicSpots(dto.getScenicSpots()),
                    normalizeFoodSpots(dto.getFoodSpots()),
                    normalizeHotelAreas(dto.getHotelAreas()),
                    normalizeTransportPlan(dto.getTransportPlan()));
        }
        Map<?, ?> map = asMap(value);
        return new RecommendationContextDTO(
                normalizeScenicSpots(map.get("scenicSpots")),
                normalizeFoodSpots(map.get("foodSpots")),
                normalizeHotelAreas(map.get("hotelAreas")),
                normalizeTransportPlan(map.get("transportPlan")));
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
        if (value == null) {
            return null;
        }
        if (value instanceof TransportPlanDTO dto) {
            return new TransportPlanDTO(
                    normalizeTravelMode(dto.getTravelMode()),
                    dto.getPickupStore(),
                    dto.getReturnStore(),
                    dto.getTips());
        }
        Map<?, ?> map = asMap(value);
        return new TransportPlanDTO(
                normalizeTravelMode(map.get("travelMode")),
                null,
                null,
                stringList(map.get("tips")));
    }

    private TravelModeDTO normalizeTravelMode(Object value) {
        if (value instanceof TravelModeDTO dto) {
            return dto;
        }
        Map<?, ?> map = asMap(value);
        return new TravelModeDTO(
                text(map, "mode"),
                bool(map, "recommended"),
                text(map, "reason"),
                stringList(map.get("tips")));
    }

    private TripPlanDTO normalizeTripPlan(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof TripPlanDTO dto) {
            return new TripPlanDTO(
                    dto.getTitle(),
                    dto.getDestination(),
                    dto.getDays(),
                    dto.getSummary(),
                    normalizeDailyPlans(dto.getDailyPlans()),
                    normalizeBudgetSummary(dto.getBudgetSummary()),
                    normalizeAccommodationSuggestion(dto.getAccommodationSuggestion()),
                    dto.getTips());
        }
        Map<?, ?> map = asMap(value);
        return new TripPlanDTO(
                text(map, "title"),
                text(map, "destination"),
                integer(map, "days"),
                text(map, "summary"),
                normalizeDailyPlans(map.get("dailyPlans")),
                normalizeBudgetSummary(map.get("budgetSummary")),
                normalizeAccommodationSuggestion(map.get("accommodationSuggestion")),
                stringList(map.get("tips")));
    }

    private List<TripPlanDTO.DailyPlan> normalizeDailyPlans(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(this::toDailyPlan).toList();
    }

    private TripPlanDTO.DailyPlan toDailyPlan(Object value) {
        if (value instanceof TripPlanDTO.DailyPlan dto) {
            return new TripPlanDTO.DailyPlan(
                    dto.getDay(),
                    dto.getTheme(),
                    dto.getFromCity(),
                    dto.getToCity(),
                    dto.getOvernightCity(),
                    dto.getDriveHoursEstimate(),
                    dto.getDriveDistanceEstimate(),
                    normalizePlanItems(dto.getItems()),
                    dto.getFoodSuggestions(),
                    dto.getEstimatedCost(),
                    dto.getDayTips());
        }
        Map<?, ?> map = asMap(value);
        return new TripPlanDTO.DailyPlan(
                integer(map, "day"),
                text(map, "theme"),
                text(map, "fromCity"),
                text(map, "toCity"),
                text(map, "overnightCity"),
                doubleValue(map, "driveHoursEstimate"),
                integer(map, "driveDistanceEstimate"),
                normalizePlanItems(map.get("items")),
                stringList(map.get("foodSuggestions")),
                integer(map, "estimatedCost"),
                stringList(map.get("dayTips")));
    }

    private List<TripPlanDTO.PlanItem> normalizePlanItems(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(this::toPlanItem).toList();
    }

    private TripPlanDTO.PlanItem toPlanItem(Object value) {
        if (value instanceof TripPlanDTO.PlanItem dto) {
            return dto;
        }
        Map<?, ?> map = asMap(value);
        return new TripPlanDTO.PlanItem(
                text(map, "time"),
                text(map, "place"),
                text(map, "activity"),
                text(map, "duration"),
                text(map, "transport"),
                integer(map, "cost"),
                text(map, "tips"));
    }

    private TripPlanDTO.BudgetSummary normalizeBudgetSummary(Object value) {
        if (value instanceof TripPlanDTO.BudgetSummary dto) {
            return dto;
        }
        Map<?, ?> map = asMap(value);
        return new TripPlanDTO.BudgetSummary(
                integer(map, "totalEstimatedCost"),
                integer(map, "transportCost"),
                integer(map, "foodCost"),
                integer(map, "ticketCost"),
                integer(map, "hotelCost"),
                text(map, "tips"));
    }

    private TripPlanDTO.AccommodationSuggestion normalizeAccommodationSuggestion(Object value) {
        if (value instanceof TripPlanDTO.AccommodationSuggestion dto) {
            return dto;
        }
        Map<?, ?> map = asMap(value);
        return new TripPlanDTO.AccommodationSuggestion(
                text(map, "area"), text(map, "reason"), text(map, "priceRange"));
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

    private Integer integer(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? null : Integer.valueOf(String.valueOf(value));
    }

    private Double doubleValue(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return value == null ? null : Double.valueOf(String.valueOf(value));
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }
}
