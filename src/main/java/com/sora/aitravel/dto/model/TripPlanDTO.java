package com.sora.aitravel.dto.model;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 前端 AI 行程页使用的完整旅行计划合同。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TripPlanDTO {

    private String title;
    private String destination;
    private Integer days;
    private String summary;
    private AccommodationSuggestion accommodationSuggestion;
    private List<DailyPlan> dailyPlans;
    private BudgetSummary budgetSummary;
    private List<String> tips;
    private DataQuality dataQuality;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyPlan {
        private Integer day;
        private String theme;
        private String intensity;
        private String intensityLabel;
        private String city;
        private String diningArea;
        private String routeSummary;
        private List<Spot> spots;
        private List<RouteLeg> routeLegs;
        private List<FoodSuggestion> foodSuggestions;
        private List<String> dayTips;
        private EstimatedCost estimatedCost;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Spot {
        private String poiId;
        private String name;
        private String type;
        private String city;
        private String area;
        private String address;
        private BigDecimal lng;
        private BigDecimal lat;
        private String coordType;
        private Integer order;
        private String startTime;
        private Integer suggestedDurationMinutes;
        private String suggestedDurationText;
        private String suggestedDurationSource;
        private String reason;
        private String tips;
        private Integer ticketCost;
        private String ticketCostText;
        private Boolean ticketCostEstimated;
        private String ticketCostSource;
        private String openingHours;
        private BigDecimal rating;
        private Integer averageCost;
        private String businessArea;
        private List<String> imageUrls;
        private BigDecimal entranceLng;
        private BigDecimal entranceLat;
        private Boolean reservationRequired;
        private List<String> tags;
        private String source;
        private BigDecimal confidence;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouteLeg {
        private Integer fromOrder;
        private Integer toOrder;
        private String mode;
        private String suggestion;
        private Integer distanceMeters;
        private Integer durationMinutes;
        private Integer estimatedCost;
        private String source;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FoodSuggestion {
        private String name;
        private String area;
        private String meal;
        private String reason;
        private BigDecimal rating;
        private Integer averageCost;
        private String openingHours;
        private String source;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EstimatedCost {
        private Integer tickets;
        private Integer food;
        private Integer transport;
        private Integer total;
        private String ticketSource;
        private String foodSource;
        private String transportSource;
        private Boolean excludesUnknownItems;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BudgetSummary {
        private Integer transportCost;
        private Integer foodCost;
        private Integer ticketCost;
        private Integer hotelCost;
        private Integer totalEstimatedCost;
        private String ticketSource;
        private String hotelSource;
        private Boolean excludesUnknownItems;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccommodationSuggestion {
        private String area;
        private String reason;
        private String priceRange;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataQuality {
        private String poiSource;
        private String routeSource;
        private String priceSource;
    }
}
