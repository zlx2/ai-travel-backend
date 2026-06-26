package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.model.TripPlanDTO;
import com.sora.aitravel.dto.response.TripGenerateResponse;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 将 Generate 各节点产物组装为前端 AI 行程页合同。 */
@Slf4j
@Component
public class GenerateResultMergeNode {

    private static final String SCHEMA_VERSION = "trip-plan-v1";

    public void execute(GenerateWorkflowContext context) {
        TravelRequirementDTO requirement = context.getRequirement();
        TripPlanDTO tripPlan = buildTripPlan(context, requirement);
        context.setResult(
                new TripGenerateResponse(
                        SCHEMA_VERSION,
                        context.getRequest().getConversationId(),
                        null,
                        requirement,
                        context.getSelectedQuote(),
                        context.getRecommendationContext(),
                        tripPlan));
        log.info("节点[result-merge]：Generate 响应已组装，schema={}, dailyPlans={}", SCHEMA_VERSION, tripPlan.getDailyPlans().size());
    }

    private TripPlanDTO buildTripPlan(
            GenerateWorkflowContext context, TravelRequirementDTO requirement) {
        List<TripPlanDTO.DailyPlan> dailyPlans =
                context.getLockedDailyPlans() == null ? List.of() : context.getLockedDailyPlans();
        TripPlanDTO.BudgetSummary budgetSummary = budgetSummary(dailyPlans, requirement);
        List<String> tips = buildTips(context);
        return new TripPlanDTO(
                displayDestination(requirement) + requirement.getDays() + "日旅行方案",
                displayDestination(requirement),
                requirement.getDays(),
                "基于高德 POI 候选、逐日强度和后端校验生成的结构化行程。",
                accommodationSuggestion(context),
                dailyPlans,
                budgetSummary,
                tips,
                dataQuality(context));
    }

    private TripPlanDTO.AccommodationSuggestion accommodationSuggestion(
            GenerateWorkflowContext context) {
        PoiCandidate hotel = null;
        if (context.getCityProfile() != null
                && context.getCityProfile().hotelCandidates() != null
                && !context.getCityProfile().hotelCandidates().isEmpty()) {
            hotel = context.getCityProfile().hotelCandidates().get(0);
        }
        if (hotel == null) {
            return null;
        }
        return new TripPlanDTO.AccommodationSuggestion(
                firstNonBlank(hotel.getArea(), hotel.getName()),
                null,
                null);
    }

    private TripPlanDTO.BudgetSummary budgetSummary(
            List<TripPlanDTO.DailyPlan> dailyPlans, TravelRequirementDTO requirement) {
        int tickets = 0;
        int food = 0;
        int transport = 0;
        for (TripPlanDTO.DailyPlan day : dailyPlans) {
            if (day.getEstimatedCost() == null) {
                continue;
            }
            tickets += value(day.getEstimatedCost().getTickets());
            food += value(day.getEstimatedCost().getFood());
            transport += value(day.getEstimatedCost().getTransport());
        }
        TripPlanDTO.BudgetSummary result = new TripPlanDTO.BudgetSummary();
        result.setTransportCost(transport);
        result.setFoodCost(food);
        result.setTicketCost(tickets);
        result.setHotelCost(null);
        result.setTotalEstimatedCost(transport + food + tickets);
        result.setTicketSource("UNAVAILABLE");
        result.setHotelSource("UNAVAILABLE");
        result.setExcludesUnknownItems(true);
        return result;
    }

    private List<String> buildTips(GenerateWorkflowContext context) {
        List<String> tips = new ArrayList<>();
        if (context.getDayValidationReports() != null) {
            for (DayPlanValidationReport report : context.getDayValidationReports()) {
                if (!Boolean.TRUE.equals(report.passed())) {
                    tips.add("第 " + report.getDay() + " 天校验提示：" + String.join("；", report.getWarnings()));
                }
            }
        }
        if (tips.isEmpty()) {
            tips.add("路线里程和实时耗时由前端高德地图计算。");
        }
        return tips;
    }

    private TripPlanDTO.DataQuality dataQuality(GenerateWorkflowContext context) {
        return new TripPlanDTO.DataQuality(
                "AMAP",
                "AMAP",
                "AMAP_AVERAGE_COST_AND_ROUTE;TICKET_HOTEL_UNAVAILABLE");
    }

    private String displayDestination(TravelRequirementDTO requirement) {
        if (requirement.getDestination() != null && !requirement.getDestination().isBlank()) {
            return requirement.getDestination();
        }
        if (requirement.getRouteRegion() != null && !requirement.getRouteRegion().isBlank()) {
            return requirement.getRouteRegion();
        }
        return String.join("-", requirement.getRouteCities());
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

}
