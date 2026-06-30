package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.model.TripPlanDTO;
import com.sora.aitravel.dto.response.TripGenerateResponse;
import java.math.BigDecimal;
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
                        tripPlan,
                        null));
        log.info(
                "节点[result-merge]：Generate 响应已组装，schema={}, dailyPlans={}",
                SCHEMA_VERSION,
                tripPlan.getDailyPlans().size());
    }

    private TripPlanDTO buildTripPlan(
            GenerateWorkflowContext context, TravelRequirementDTO requirement) {
        List<TripPlanDTO.DailyPlan> dailyPlans =
                context.getLockedDailyPlans() == null
                        ? List.of()
                        : new ArrayList<>(context.getLockedDailyPlans());
        injectRentalPickupSpot(context, dailyPlans);
        injectIntercityTransferSpots(dailyPlans);
        injectRentalReturnSpot(context, dailyPlans);
        TripPlanDTO.BudgetSummary budgetSummary = budgetSummary(context, dailyPlans, requirement);
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
                firstNonBlank(hotel.getArea(), hotel.getName()), null, null);
    }

    private TripPlanDTO.BudgetSummary budgetSummary(
            GenerateWorkflowContext context,
            List<TripPlanDTO.DailyPlan> dailyPlans,
            TravelRequirementDTO requirement) {
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
        if (context.getSelectedQuote() != null
                && context.getSelectedQuote().getFeeBreakdown() != null
                && context.getSelectedQuote().getFeeBreakdown().getTotalPriceCent() != null) {
            transport += context.getSelectedQuote().getFeeBreakdown().getTotalPriceCent() / 100;
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

    private void injectRentalPickupSpot(
            GenerateWorkflowContext context, List<TripPlanDTO.DailyPlan> dailyPlans) {
        if (context.getRentalTripContext() == null
                || context.getRentalTripContext().getPickupPlan() == null
                || dailyPlans.isEmpty()) {
            return;
        }
        TripPlanDTO.DailyPlan firstDay = dailyPlans.get(0);
        List<TripPlanDTO.Spot> spots =
                firstDay.getSpots() == null ? new ArrayList<>() : new ArrayList<>(firstDay.getSpots());
        boolean alreadyInjected =
                spots.stream().anyMatch(spot -> "RENTAL_PICKUP".equals(spot.getType()));
        if (alreadyInjected) {
            normalizeSpotOrders(firstDay, false);
            return;
        }

        TripPlanDTO.Spot pickup = new TripPlanDTO.Spot();
        pickup.setName(context.getRentalTripContext().getPickupPlan().getTitle());
        pickup.setType("RENTAL_PICKUP");
        pickup.setOrder(1);
        pickup.setStartTime(pickupStartTime(context.getRentalTripContext().getArrivalTimeRange()));
        pickup.setSuggestedDurationMinutes(30);
        pickup.setSuggestedDurationText("约30分钟");
        pickup.setReason(context.getRentalTripContext().getPickupPlan().getDisplayText());
        pickup.setTips("完成身份核验、验车和交车后开始自驾行程。");
        pickup.setSource("RENTAL_CONTEXT");
        pickup.setTags(List.of("接车", "送车接人", "租车"));
        if (context.getRentalTripContext().getArrivalPoint() != null) {
            pickup.setAddress(context.getRentalTripContext().getArrivalPoint().getName());
            pickup.setCity(context.getRentalTripContext().getArrivalPoint().getCityName());
        }
        if (context.getRentalTripContext().getMatchedStore() != null) {
            pickup.setArea(context.getRentalTripContext().getMatchedStore().getDisplayName());
            pickup.setLng(decimal(context.getRentalTripContext().getMatchedStore().getLng()));
            pickup.setLat(decimal(context.getRentalTripContext().getMatchedStore().getLat()));
            pickup.setEntranceLng(pickup.getLng());
            pickup.setEntranceLat(pickup.getLat());
            pickup.setCoordType("GCJ02");
        }

        spots.add(0, pickup);
        firstDay.setSpots(spots);
        normalizeSpotOrders(firstDay, true);
        firstDay.setRouteSummary("接车交付 → " + firstNonBlank(firstDay.getRouteSummary(), "开始自驾游玩"));
    }

    private String pickupStartTime(String arrivalTimeRange) {
        String value = arrivalTimeRange == null ? "" : arrivalTimeRange;
        if (value.contains(":")) {
            return value;
        }
        if (value.contains("中午")) {
            return "12:30";
        }
        if (value.contains("下午")) {
            return "14:30";
        }
        if (value.contains("晚上")) {
            return "18:30";
        }
        return "09:30";
    }

    private void injectRentalReturnSpot(
            GenerateWorkflowContext context, List<TripPlanDTO.DailyPlan> dailyPlans) {
        if (context.getRentalTripContext() == null || dailyPlans.isEmpty()) {
            return;
        }
        TripPlanDTO.DailyPlan lastDay = dailyPlans.get(dailyPlans.size() - 1);
        List<TripPlanDTO.Spot> spots =
                lastDay.getSpots() == null ? new ArrayList<>() : new ArrayList<>(lastDay.getSpots());
        boolean alreadyInjected =
                spots.stream().anyMatch(spot -> "RENTAL_RETURN".equals(spot.getType()));
        if (alreadyInjected) {
            return;
        }

        int nextOrder = spots.stream().map(TripPlanDTO.Spot::getOrder).mapToInt(this::value).max().orElse(0) + 1;
        TripPlanDTO.Spot returnSpot = new TripPlanDTO.Spot();
        returnSpot.setName(returnTitle(context));
        returnSpot.setType("RENTAL_RETURN");
        returnSpot.setOrder(nextOrder);
        returnSpot.setStartTime("行程结束前");
        returnSpot.setSuggestedDurationMinutes(30);
        returnSpot.setSuggestedDurationText("约30分钟");
        returnSpot.setReason(returnReason(context));
        returnSpot.setTips("请预留验车、交接和个人物品检查时间。");
        returnSpot.setSource("RENTAL_CONTEXT");
        returnSpot.setTags(List.of("还车", "租车", "行程收束"));
        if (context.getRentalTripContext().getReturnPoint() != null) {
            returnSpot.setAddress(context.getRentalTripContext().getReturnPoint());
        } else if (context.getRentalTripContext().getArrivalPoint() != null) {
            returnSpot.setAddress(context.getRentalTripContext().getArrivalPoint().getName());
            returnSpot.setCity(context.getRentalTripContext().getArrivalPoint().getCityName());
        }
        if (context.getRentalTripContext().getMatchedStore() != null) {
            returnSpot.setArea(context.getRentalTripContext().getMatchedStore().getDisplayName());
            returnSpot.setLng(decimal(context.getRentalTripContext().getMatchedStore().getLng()));
            returnSpot.setLat(decimal(context.getRentalTripContext().getMatchedStore().getLat()));
            returnSpot.setEntranceLng(returnSpot.getLng());
            returnSpot.setEntranceLat(returnSpot.getLat());
            returnSpot.setCoordType("GCJ02");
        }

        spots.add(returnSpot);
        lastDay.setSpots(spots);
        normalizeSpotOrders(lastDay, false);
        lastDay.setRouteSummary(firstNonBlank(lastDay.getRouteSummary(), "当天游玩") + " → 还车交接");
    }

    private void injectIntercityTransferSpots(List<TripPlanDTO.DailyPlan> dailyPlans) {
        for (int index = 1; index < dailyPlans.size(); index++) {
            TripPlanDTO.DailyPlan previousDay = dailyPlans.get(index - 1);
            TripPlanDTO.DailyPlan currentDay = dailyPlans.get(index);
            String previousCity = normalizedCity(previousDay.getCity());
            String currentCity = normalizedCity(currentDay.getCity());
            if (previousCity.isBlank() || currentCity.isBlank() || previousCity.equals(currentCity)) {
                continue;
            }
            List<TripPlanDTO.Spot> spots =
                    currentDay.getSpots() == null
                            ? new ArrayList<>()
                            : new ArrayList<>(currentDay.getSpots());
            if (spots.stream().anyMatch(spot -> "INTERCITY_TRANSFER".equals(spot.getType()))) {
                normalizeSpotOrders(currentDay, false);
                continue;
            }
            TripPlanDTO.Spot firstSpot = firstRouteAnchor(spots);
            TripPlanDTO.Spot previousAnchor = lastRouteAnchor(previousDay.getSpots());
            TripPlanDTO.Spot transfer = new TripPlanDTO.Spot();
            transfer.setName(
                    firstNonBlank(previousAnchor == null ? null : previousAnchor.getName(), previousDay.getCity() + "住宿点")
                            + " → "
                            + firstNonBlank(firstSpot == null ? null : firstSpot.getName(), currentDay.getCity() + "首站")
                            + "路程");
            transfer.setType("INTERCITY_TRANSFER");
            transfer.setOrder(1);
            transfer.setStartTime("08:30");
            transfer.setSuggestedDurationMinutes(120);
            transfer.setSuggestedDurationText("约2小时");
            transfer.setReason(
                    "跨城市行程按顺路推进处理：前一天住在"
                            + previousDay.getCity()
                            + "，第二天早上从住宿点出发前往"
                            + currentDay.getCity()
                            + firstNonBlank(firstSpot == null ? null : firstSpot.getName(), "首个景点")
                            + "，不在同一天来回跨城。");
            transfer.setTips("实际跨城耗时以当天高德实时路况为准，建议预留加油、停车和休息缓冲。");
            transfer.setSource("INTERCITY_RULE");
            transfer.setCity(currentDay.getCity());
            transfer.setTags(List.of("跨城路程", "自驾", "换城市"));
            if (previousAnchor != null) {
                transfer.setAddress(previousAnchor.getAddress());
                transfer.setArea(previousAnchor.getArea());
                transfer.setLng(previousAnchor.getLng());
                transfer.setLat(previousAnchor.getLat());
                transfer.setEntranceLng(previousAnchor.getEntranceLng());
                transfer.setEntranceLat(previousAnchor.getEntranceLat());
                transfer.setCoordType(previousAnchor.getCoordType());
            }
            spots.add(0, transfer);
            currentDay.setSpots(spots);
            normalizeSpotOrders(currentDay, true);
            currentDay.setRouteSummary(
                    previousDay.getCity()
                            + "住宿点 → "
                            + firstNonBlank(currentDay.getRouteSummary(), currentDay.getCity() + "首站"));
        }
    }

    private String normalizedCity(String value) {
        return value == null ? "" : value.replace("市", "").replaceAll("\\s+", "").trim();
    }

    private TripPlanDTO.Spot firstRouteAnchor(List<TripPlanDTO.Spot> spots) {
        if (spots == null) {
            return null;
        }
        return spots.stream()
                .filter(this::hasLocation)
                .filter(spot -> !"INTERCITY_TRANSFER".equals(spot.getType()))
                .findFirst()
                .orElse(null);
    }

    private TripPlanDTO.Spot lastRouteAnchor(List<TripPlanDTO.Spot> spots) {
        if (spots == null) {
            return null;
        }
        for (int index = spots.size() - 1; index >= 0; index--) {
            TripPlanDTO.Spot spot = spots.get(index);
            if (hasLocation(spot)
                    && !"RENTAL_RETURN".equals(spot.getType())
                    && !"INTERCITY_TRANSFER".equals(spot.getType())) {
                return spot;
            }
        }
        return null;
    }

    private boolean hasLocation(TripPlanDTO.Spot spot) {
        return spot != null && spot.getLng() != null && spot.getLat() != null;
    }

    private void normalizeSpotOrders(TripPlanDTO.DailyPlan day, boolean shiftExistingRouteLegs) {
        if (day == null || day.getSpots() == null) {
            return;
        }
        for (int index = 0; index < day.getSpots().size(); index++) {
            day.getSpots().get(index).setOrder(index + 1);
        }
        if (shiftExistingRouteLegs && day.getRouteLegs() != null) {
            day.getRouteLegs()
                    .forEach(
                            leg -> {
                                leg.setFromOrder(value(leg.getFromOrder()) + 1);
                                leg.setToOrder(value(leg.getToOrder()) + 1);
                            });
        }
    }

    private String returnTitle(GenerateWorkflowContext context) {
        String mode = context.getRentalTripContext().getReturnMode();
        if (mode.contains("异地")) {
            return "异地还车";
        }
        return "同城还车";
    }

    private String returnReason(GenerateWorkflowContext context) {
        String point =
                firstNonBlank(
                        context.getRentalTripContext().getReturnPoint(),
                        context.getRentalTripContext().getArrivalPoint() == null
                                ? null
                                : context.getRentalTripContext().getArrivalPoint().getName());
        return returnTitle(context)
                + "安排在"
                + firstNonBlank(point, "行程收束点")
                + "附近，便于完成验车和交接后衔接返程。";
    }

    private BigDecimal decimal(String value) {
        try {
            return value == null || value.isBlank() ? null : new BigDecimal(value);
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> buildTips(GenerateWorkflowContext context) {
        List<String> tips = new ArrayList<>();
        if (context.getDayValidationReports() != null) {
            for (DayPlanValidationReport report : context.getDayValidationReports()) {
                if (!Boolean.TRUE.equals(report.passed())) {
                    tips.add(
                            "第 "
                                    + report.getDay()
                                    + " 天校验提示："
                                    + String.join("；", report.getWarnings()));
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
                "AMAP", "AMAP", "AMAP_AVERAGE_COST_AND_ROUTE;TICKET_HOTEL_UNAVAILABLE");
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
