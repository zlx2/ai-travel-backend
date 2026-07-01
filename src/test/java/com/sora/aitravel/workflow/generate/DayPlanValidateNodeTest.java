package com.sora.aitravel.workflow.generate;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.sora.aitravel.dto.model.RentalQuoteOptionDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.model.TripPlanDTO;
import com.sora.aitravel.workflow.generate.route.RouteShapeValidator;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DayPlanValidateNodeTest {

    @Test
    @DisplayName("路线过长类 warning 不作为生成硬失败抛给前端")
    void shouldNotThrowWhenRentalRouteHasLongLegWarning() {
        DayPlanValidateNode node = new DayPlanValidateNode(new RouteShapeValidator(), new PoiIdentityService());
        GenerateWorkflowContext context = new GenerateWorkflowContext();
        TravelRequirementDTO requirement = new TravelRequirementDTO();
        requirement.setDays(1);
        context.setRequirement(requirement);
        context.setSelectedQuote(new RentalQuoteOptionDTO());
        context.setSingleDayGeneration(true);

        TripPlanDTO.DailyPlan day = new TripPlanDTO.DailyPlan();
        day.setDay(1);
        day.setIntensity("LIGHT");
        day.setSpots(List.of(
                spot(1, "远途景点A", "120.0000", "30.0000"),
                spot(2, "远途景点B", "121.2000", "30.9000")));
        day.setRouteLegs(List.of(new TripPlanDTO.RouteLeg(
                1, 2, "DRIVING", "自驾约2小时", 140_000, 120, 60, "ESTIMATED")));
        context.setLockedDailyPlans(List.of(day));
        context.setRankedDayDataPackages(List.of(new DayDataPackage(
                1,
                List.of(
                        poi("远途景点A", "120.0000,30.0000"),
                        poi("远途景点B", "121.2000,30.9000")),
                List.of(),
                List.of(),
                List.of())));

        node.execute(context);

        assertFalse(context.getDayValidationReports().get(0).getPassed());
    }

    private TripPlanDTO.Spot spot(int order, String name, String lng, String lat) {
        TripPlanDTO.Spot spot = new TripPlanDTO.Spot();
        spot.setOrder(order);
        spot.setName(name);
        spot.setPoiId(name);
        spot.setLng(new BigDecimal(lng));
        spot.setLat(new BigDecimal(lat));
        return spot;
    }

    private PoiCandidate poi(String name, String location) {
        PoiCandidate candidate = new PoiCandidate();
        candidate.setName(name);
        candidate.setSourcePoiId(name);
        candidate.setLocation(location);
        return candidate;
    }
}
