package com.sora.aitravel.dto.model;

import java.util.List;

public record TripPlanDTO(
        String title,
        String destination,
        Integer days,
        String summary,
        List<DailyPlan> dailyPlans,
        BudgetSummary budgetSummary,
        AccommodationSuggestion accommodationSuggestion,
        List<String> tips) {
    public record DailyPlan(
            Integer day,
            String theme,
            List<PlanItem> items,
            List<String> foodSuggestions,
            Integer estimatedCost,
            List<String> dayTips) {}

    public record PlanItem(
            String time,
            String place,
            String activity,
            String duration,
            String transport,
            Integer cost,
            String tips) {}

    public record BudgetSummary(
            Integer totalEstimatedCost,
            Integer transportCost,
            Integer foodCost,
            Integer ticketCost,
            Integer hotelCost,
            String tips) {}

    public record AccommodationSuggestion(String area, String reason, String priceRange) {}
}
