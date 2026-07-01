package com.sora.aitravel.workflow.generate.route;

import static org.assertj.core.api.Assertions.assertThat;

import com.sora.aitravel.dto.model.TripPlanDTO;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class RouteShapeValidatorTest {

    private final RouteShapeValidator validator = new RouteShapeValidator();

    @Test
    void shouldAllowLongButReasonableRentalDayRoute() {
        TripPlanDTO.DailyPlan plan = new TripPlanDTO.DailyPlan();
        plan.setSpots(List.of(
                spot(1, "成都东站", "104.141,30.629"),
                spot(2, "都江堰景区", "103.616,31.001"),
                spot(3, "青城山", "103.570,30.905")));
        plan.setRouteLegs(List.of(
                leg(1, 2, 82_000),
                leg(2, 3, 25_000)));

        List<String> warnings = validator.validate(plan, true);

        assertThat(warnings).doesNotContain("当天路线总距离过长");
        assertThat(warnings).doesNotContain("前端地图路线总距离过长");
    }

    private TripPlanDTO.Spot spot(int order, String name, String location) {
        String[] parts = location.split(",");
        TripPlanDTO.Spot spot = new TripPlanDTO.Spot();
        spot.setOrder(order);
        spot.setName(name);
        spot.setLng(new BigDecimal(parts[0]));
        spot.setLat(new BigDecimal(parts[1]));
        return spot;
    }

    private TripPlanDTO.RouteLeg leg(int from, int to, int meters) {
        TripPlanDTO.RouteLeg leg = new TripPlanDTO.RouteLeg();
        leg.setFromOrder(from);
        leg.setToOrder(to);
        leg.setDistanceMeters(meters);
        leg.setDurationMinutes(Math.max(1, meters / 1000));
        leg.setMode("DRIVING");
        return leg;
    }
}
