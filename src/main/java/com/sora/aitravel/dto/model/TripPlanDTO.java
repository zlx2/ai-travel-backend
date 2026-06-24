package com.sora.aitravel.dto.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 完整旅行计划 DTO。
 *
 * <p>由 AI 生成或用户保存的完整行程计划，包含每日安排、预算汇总和住宿建议。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TripPlanDTO {

    private String title;
    private String destination;
    private Integer days;
    private String summary;
    private List<DailyPlan> dailyPlans;
    private BudgetSummary budgetSummary;
    private AccommodationSuggestion accommodationSuggestion;
    private List<String> tips;

    /** 每日计划 DTO。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyPlan {

        private Integer day;
        private String theme;
        private String fromCity;
        private String toCity;
        private String overnightCity;
        private Double driveHoursEstimate;
        private Integer driveDistanceEstimate;
        private List<PlanItem> items;
        private List<String> foodSuggestions;
        private Integer estimatedCost;
        private List<String> dayTips;
    }

    /** 行程项目 DTO（每日计划中的单个活动）。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlanItem {

        private String time;
        private String place;
        private String activity;
        private String duration;
        private String transport;
        private Integer cost;
        private String tips;
    }

    /** 预算汇总 DTO。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BudgetSummary {

        private Integer totalEstimatedCost;
        private Integer transportCost;
        private Integer foodCost;
        private Integer ticketCost;
        private Integer hotelCost;
        private String tips;
    }

    /** 住宿建议 DTO。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccommodationSuggestion {

        private String area;
        private String reason;
        private String priceRange;
    }
}
