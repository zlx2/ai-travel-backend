package com.sora.aitravel.workflow.generate.route;

import com.sora.aitravel.dto.model.TripPlanDTO;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Builds generation-time route leg estimates from ordered daily spots. */
@Component
public class RouteLegEstimateFactory {
    private static final String MODE_WALK = "WALK";
    private static final String MODE_TAXI = "TAXI";
    private static final String MODE_DRIVING = "DRIVING";
    private static final String SOURCE_ESTIMATED = "ESTIMATED";
    private static final double WALKING_ROUTE_MAX_KM = 2.0;

    public List<TripPlanDTO.RouteLeg> build(List<TripPlanDTO.Spot> spots, boolean rentalEnabled) {
        List<TripPlanDTO.RouteLeg> legs = new ArrayList<>();
        if (spots == null || spots.size() < 2) {
            return legs;
        }
        for (int index = 0; index < spots.size() - 1; index++) {
            TripPlanDTO.Spot from = spots.get(index);
            TripPlanDTO.Spot to = spots.get(index + 1);
            TripPlanDTO.RouteLeg leg = buildLeg(from, to, rentalEnabled);
            legs.add(leg);
        }
        return legs;
    }

    private TripPlanDTO.RouteLeg buildLeg(
            TripPlanDTO.Spot from, TripPlanDTO.Spot to, boolean rentalEnabled) {
        double[] fromLocation = spotLocation(from);
        double[] toLocation = spotLocation(to);
        RouteEstimate estimate = estimate(fromLocation, toLocation, rentalEnabled);
        TripPlanDTO.RouteLeg leg = new TripPlanDTO.RouteLeg();
        leg.setFromOrder(from.getOrder());
        leg.setToOrder(to.getOrder());
        leg.setMode(estimate.mode());
        leg.setSuggestion("从" + from.getName() + "前往" + to.getName() + "，" + estimate.description());
        leg.setDistanceMeters(estimate.distanceMeters());
        leg.setDurationMinutes(estimate.durationMinutes());
        leg.setEstimatedCost(estimate.cost());
        leg.setSource(SOURCE_ESTIMATED);
        return leg;
    }

    private RouteEstimate estimate(double[] from, double[] to, boolean rentalEnabled) {
        if (from == null || to == null) {
            return new RouteEstimate(
                    Integer.MAX_VALUE / 4,
                    null,
                    null,
                    "UNKNOWN",
                    rentalEnabled ? "坐标不足，建议自驾衔接。" : "坐标不足，建议灵活选择步行或打车。");
        }
        double directKm = GeoRouteCalculator.distanceKm(from[0], from[1], to[0], to[1]);
        boolean walking = !rentalEnabled && directKm <= WALKING_ROUTE_MAX_KM;
        int distanceMeters =
                (int)
                        Math.round(
                                directKm
                                        * (walking
                                                ? 1.15
                                                : GeoRouteCalculator.DEFAULT_ROAD_DISTANCE_FACTOR)
                                        * 1000);
        double speedKmh =
                walking
                        ? GeoRouteCalculator.WALKING_SPEED_KMH
                        : rentalEnabled
                                ? GeoRouteCalculator.DEFAULT_DRIVING_SPEED_KMH
                                : GeoRouteCalculator.CITY_DRIVING_SPEED_KMH;
        int durationMinutes =
                (int) Math.ceil(GeoRouteCalculator.travelSeconds(distanceMeters, speedKmh) / 60.0);
        Integer cost =
                walking ? 0 : rentalEnabled ? estimateDrivingCost(distanceMeters) : estimateTaxiCost(distanceMeters);
        String mode = walking ? MODE_WALK : rentalEnabled ? MODE_DRIVING : MODE_TAXI;
        return new RouteEstimate(
                distanceMeters,
                durationMinutes,
                cost,
                mode,
                description(distanceMeters, durationMinutes, cost, walking, rentalEnabled));
    }

    private double[] spotLocation(TripPlanDTO.Spot spot) {
        if (spot == null) {
            return null;
        }
        java.math.BigDecimal lng = spot.getEntranceLng() == null ? spot.getLng() : spot.getEntranceLng();
        java.math.BigDecimal lat = spot.getEntranceLat() == null ? spot.getLat() : spot.getEntranceLat();
        if (lng == null || lat == null) {
            return null;
        }
        return new double[] {lng.doubleValue(), lat.doubleValue()};
    }

    private String description(
            int distanceMeters, Integer durationMinutes, Integer cost, boolean walking, boolean rentalEnabled) {
        return formatDistance(distanceMeters)
                + "，约 "
                + durationMinutes
                + " 分钟"
                + (walking ? "，距离较近，可步行或短途打车" : "")
                + (!walking && !rentalEnabled && cost != null ? "，打车约 ¥" + cost : "")
                + (rentalEnabled && cost != null ? "，自驾能耗/油费约 ¥" + cost : "");
    }

    private Integer estimateDrivingCost(Integer distanceMeters) {
        return distanceMeters == null ? null : Math.max(3, (int) Math.ceil(distanceMeters / 1000.0 * 0.8));
    }

    private Integer estimateTaxiCost(Integer distanceMeters) {
        if (distanceMeters == null) {
            return null;
        }
        double km = distanceMeters / 1000.0;
        return (int) Math.ceil(13 + Math.max(0, km - 3) * 2.5);
    }

    private String formatDistance(int meters) {
        if (meters >= 1000) {
            return String.format("%.1f 公里", meters / 1000.0);
        }
        return meters + " 米";
    }

    private static final class RouteEstimate {
        private final int distanceMeters;
        private final Integer durationMinutes;
        private final Integer cost;
        private final String mode;
        private final String description;

        private RouteEstimate(
                int distanceMeters,
                Integer durationMinutes,
                Integer cost,
                String mode,
                String description) {
            this.distanceMeters = distanceMeters;
            this.durationMinutes = durationMinutes;
            this.cost = cost;
            this.mode = mode;
            this.description = description;
        }

        private int distanceMeters() {
            return distanceMeters;
        }

        private Integer durationMinutes() {
            return durationMinutes;
        }

        private Integer cost() {
            return cost;
        }

        private String mode() {
            return mode;
        }

        private String description() {
            return description;
        }
    }
}
