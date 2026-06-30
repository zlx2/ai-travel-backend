package com.sora.aitravel.workflow.generate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.model.TripPlanDTO;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TripTimelineAssemblerTest {

    @Test
    @DisplayName("时间轴不输出跨天时间，住宿优先贴近最后景点")
    void shouldClampTimelineAndPreferHotelNearLastSpot() {
        TripTimelineAssembler assembler = new TripTimelineAssembler();
        GenerateWorkflowContext context = new GenerateWorkflowContext();
        TravelRequirementDTO requirement = new TravelRequirementDTO();
        requirement.setDestination("杭州");
        requirement.setDays(1);
        requirement.setPeopleCount(2);
        context.setRequirement(requirement);

        TripPlanDTO.DailyPlan day = new TripPlanDTO.DailyPlan();
        day.setDay(1);
        day.setCity("杭州");
        day.setSpots(List.of(
                spot(1, "景点A", "120.0000", "30.0000", 180),
                spot(2, "景点B", "120.0100", "30.0100", 180),
                spot(3, "景点C", "120.0200", "30.0200", 180),
                spot(4, "景点D", "120.0300", "30.0300", 180)));
        day.setFoodSuggestions(List.of(
                foodSuggestion("午餐", "LUNCH", "120.0110,30.0110"),
                foodSuggestion("晚餐", "DINNER", "120.0310,30.0310")));
        context.setLockedDailyPlans(List.of(day));
        context.setRankedDayDataPackages(List.of(new DayDataPackage(
                1,
                List.of(),
                List.of(food("午餐", "LUNCH", "120.0110,30.0110"), food("晚餐", "DINNER", "120.0310,30.0310")),
                List.of(
                        poi("远处酒店", "121.0000,31.0000"),
                        poi("近处酒店", "120.0315,30.0315")),
                List.of())));
        DaySkeleton skeleton = new DaySkeleton();
        skeleton.setDay(1);
        skeleton.setStayArea(new AreaAnchorSnapshot(
                "far-stay", "远处住宿区", "STAY", "杭州", "远处", "远处", "121.0000,31.0000"));
        context.setDaySkeletons(List.of(skeleton));

        assembler.execute(context);

        List<TripPlanDTO.TimelineNode> timeline = day.getTimeline();
        assertFalse(timeline.isEmpty());
        assertTrue(timeline.stream().noneMatch(node -> node.getStartTime() != null && node.getStartTime().startsWith("00:")));
        TripPlanDTO.TimelineNode stay = timeline.stream()
                .filter(node -> "STAY_AREA".equals(node.getType()))
                .findFirst()
                .orElseThrow();
        assertEquals(new BigDecimal("120.0315"), stay.getLng());
        assertEquals(new BigDecimal("30.0315"), stay.getLat());
        assertTrue(parseMinutes(stay.getStartTime()) <= 21 * 60);
    }

    private TripPlanDTO.Spot spot(int order, String name, String lng, String lat, int duration) {
        TripPlanDTO.Spot spot = new TripPlanDTO.Spot();
        spot.setOrder(order);
        spot.setName(name);
        spot.setCity("杭州");
        spot.setArea("西湖");
        spot.setLng(new BigDecimal(lng));
        spot.setLat(new BigDecimal(lat));
        spot.setSuggestedDurationMinutes(duration);
        spot.setSuggestedDurationText("约3小时");
        spot.setType("SCENIC");
        spot.setSource("AMAP");
        return spot;
    }

    private PoiCandidate food(String name, String meal, String location) {
        PoiCandidate candidate = poi(name, location);
        candidate.setCategory(meal);
        candidate.setAverageCost(80);
        return candidate;
    }

    private TripPlanDTO.FoodSuggestion foodSuggestion(String name, String meal, String location) {
        BigDecimal[] lngLat = parseLocation(location);
        return new TripPlanDTO.FoodSuggestion(
                name, "西湖", meal, null, null, 80, null, "AMAP", "杭州", "西湖", lngLat[0], lngLat[1], "GCJ02");
    }

    private PoiCandidate poi(String name, String location) {
        PoiCandidate candidate = new PoiCandidate();
        candidate.setName(name);
        candidate.setArea("西湖");
        candidate.setCity("杭州");
        candidate.setLocation(location);
        candidate.setSource("AMAP");
        return candidate;
    }

    private BigDecimal[] parseLocation(String location) {
        String[] parts = location.split(",");
        return new BigDecimal[] {new BigDecimal(parts[0]), new BigDecimal(parts[1])};
    }

    private int parseMinutes(String time) {
        String[] parts = time.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }
}
