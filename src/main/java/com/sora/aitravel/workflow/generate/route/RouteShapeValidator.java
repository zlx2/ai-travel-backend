package com.sora.aitravel.workflow.generate.route;

import com.sora.aitravel.dto.model.TripPlanDTO;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/** Validates that a generated daily route is complete and not obviously inefficient. */
@Component
public class RouteShapeValidator {
    private static final int CITY_MAX_TOTAL_DISTANCE_METERS = 45_000;
    private static final int RENTAL_MAX_TOTAL_DISTANCE_METERS = 160_000;
    private static final int CITY_MAX_SINGLE_LEG_METERS = 22_000;
    private static final int RENTAL_MAX_SINGLE_LEG_METERS = 90_000;
    private static final double MAX_ROUTE_OVER_OPTIMAL_RATIO = 1.45;

    public List<String> validate(TripPlanDTO.DailyPlan dailyPlan, boolean rentalEnabled) {
        List<String> warnings = new ArrayList<>();
        if (dailyPlan == null) {
            warnings.add("当天行程为空");
            return warnings;
        }
        List<TripPlanDTO.Spot> spots = orderedSpots(dailyPlan.getSpots());
        List<TripPlanDTO.RouteLeg> legs =
                dailyPlan.getRouteLegs() == null ? List.of() : dailyPlan.getRouteLegs();
        if (spots.size() >= 2 && legs.size() != spots.size() - 1) {
            warnings.add("路线段数量与景点数量不匹配");
        }
        int totalDistance = 0;
        int maxLegDistance = 0;
        for (TripPlanDTO.RouteLeg leg : legs) {
            if (leg.getDistanceMeters() == null || leg.getDistanceMeters() <= 0) {
                warnings.add("路线段缺少有效距离");
            } else {
                totalDistance += leg.getDistanceMeters();
                maxLegDistance = Math.max(maxLegDistance, leg.getDistanceMeters());
            }
            if (leg.getDurationMinutes() == null || leg.getDurationMinutes() <= 0) {
                warnings.add("路线段缺少有效耗时");
            }
            if (leg.getFromOrder() == null || leg.getToOrder() == null) {
                warnings.add("路线段缺少起止顺序");
            }
        }
        int maxTotal = rentalEnabled ? RENTAL_MAX_TOTAL_DISTANCE_METERS : CITY_MAX_TOTAL_DISTANCE_METERS;
        int maxSingle = rentalEnabled ? RENTAL_MAX_SINGLE_LEG_METERS : CITY_MAX_SINGLE_LEG_METERS;
        if (totalDistance > maxTotal) {
            warnings.add("当天路线总距离过长");
        }
        if (maxLegDistance > maxSingle) {
            warnings.add("当天存在过长单段路线");
        }
        if (hasBacktracking(spots)) {
            warnings.add("当天路线存在明显折返");
        }
        warnings.addAll(validateTimelineShape(dailyPlan, rentalEnabled));
        return warnings;
    }

    private List<String> validateTimelineShape(TripPlanDTO.DailyPlan dailyPlan, boolean rentalEnabled) {
        List<TimelinePoint> points = timelinePoints(dailyPlan);
        if (points.size() < 4) {
            return List.of();
        }
        List<String> warnings = new ArrayList<>();
        int actualMeters = routeMeters(points);
        int bestMeters = bestFixedEndpointMeters(points);
        if (bestMeters > 0 && actualMeters > bestMeters * MAX_ROUTE_OVER_OPTIMAL_RATIO) {
            warnings.add("前端地图路线顺序明显绕路");
        }
        int maxTotal = rentalEnabled ? RENTAL_MAX_TOTAL_DISTANCE_METERS : CITY_MAX_TOTAL_DISTANCE_METERS;
        if (actualMeters > maxTotal) {
            warnings.add("前端地图路线总距离过长");
        }
        return warnings;
    }

    private List<TimelinePoint> timelinePoints(TripPlanDTO.DailyPlan dailyPlan) {
        if (dailyPlan.getTimeline() == null) {
            return List.of();
        }
        return dailyPlan.getTimeline().stream()
                .filter(node -> node.getLng() != null && node.getLat() != null)
                .sorted(Comparator.comparing(node -> node.getOrder() == null ? Integer.MAX_VALUE : node.getOrder()))
                .map(node -> new TimelinePoint(node.getLng().doubleValue(), node.getLat().doubleValue()))
                .toList();
    }

    private int routeMeters(List<TimelinePoint> points) {
        int meters = 0;
        for (int index = 0; index < points.size() - 1; index++) {
            meters += distanceMeters(points.get(index), points.get(index + 1));
        }
        return meters;
    }

    private int bestFixedEndpointMeters(List<TimelinePoint> points) {
        TimelinePoint start = points.get(0);
        TimelinePoint end = points.get(points.size() - 1);
        List<TimelinePoint> remaining = new ArrayList<>(points.subList(1, points.size() - 1));
        int best = Integer.MAX_VALUE;
        best = Math.min(best, nearestNeighborMeters(start, remaining, end));
        for (TimelinePoint candidateStart : remaining) {
            List<TimelinePoint> candidates = new ArrayList<>(remaining);
            candidates.remove(candidateStart);
            int meters = distanceMeters(start, candidateStart) + nearestNeighborMeters(candidateStart, candidates, end);
            best = Math.min(best, meters);
        }
        return best == Integer.MAX_VALUE ? 0 : best;
    }

    private int nearestNeighborMeters(TimelinePoint start, List<TimelinePoint> middle, TimelinePoint end) {
        List<TimelinePoint> remaining = new ArrayList<>(middle);
        TimelinePoint cursor = start;
        int meters = 0;
        while (!remaining.isEmpty()) {
            TimelinePoint current = cursor;
            TimelinePoint next = remaining.stream()
                    .min(Comparator.comparingInt(point -> distanceMeters(current, point)))
                    .orElseThrow();
            meters += distanceMeters(cursor, next);
            cursor = next;
            remaining.remove(next);
        }
        meters += distanceMeters(cursor, end);
        return meters;
    }

    private int distanceMeters(TimelinePoint from, TimelinePoint to) {
        return GeoRouteCalculator.roadDistanceMeters(from.lng(), from.lat(), to.lng(), to.lat());
    }

    private boolean hasBacktracking(List<TripPlanDTO.Spot> spots) {
        if (spots.size() < 4) {
            return false;
        }
        double[] start = spotLocation(spots.get(0));
        if (start == null) {
            return false;
        }
        double farthestSeen = 0;
        boolean returnedNearStart = false;
        for (int index = 1; index < spots.size(); index++) {
            double[] current = spotLocation(spots.get(index));
            if (current == null) {
                return false;
            }
            double distanceFromStart =
                    GeoRouteCalculator.distanceKm(start[0], start[1], current[0], current[1]);
            if (farthestSeen >= 12 && distanceFromStart < farthestSeen * 0.45) {
                returnedNearStart = true;
            }
            if (returnedNearStart && distanceFromStart > farthestSeen * 0.8) {
                return true;
            }
            farthestSeen = Math.max(farthestSeen, distanceFromStart);
        }
        return false;
    }

    private List<TripPlanDTO.Spot> orderedSpots(List<TripPlanDTO.Spot> spots) {
        if (spots == null) {
            return List.of();
        }
        return spots.stream()
                .sorted(Comparator.comparing(spot -> spot.getOrder() == null ? Integer.MAX_VALUE : spot.getOrder()))
                .toList();
    }

    private double[] spotLocation(TripPlanDTO.Spot spot) {
        if (spot == null) {
            return null;
        }
        BigDecimal lng = spot.getEntranceLng() == null ? spot.getLng() : spot.getEntranceLng();
        BigDecimal lat = spot.getEntranceLat() == null ? spot.getLat() : spot.getEntranceLat();
        if (lng == null || lat == null) {
            return null;
        }
        return new double[] {lng.doubleValue(), lat.doubleValue()};
    }

    private static final class TimelinePoint {
        private final double lng;
        private final double lat;

        private TimelinePoint(double lng, double lat) {
            this.lng = lng;
            this.lat = lat;
        }

        private double lng() {
            return lng;
        }

        private double lat() {
            return lat;
        }
    }
}
