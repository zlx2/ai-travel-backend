package com.sora.aitravel.workflow.generate.route;

import com.sora.aitravel.workflow.generate.AreaAnchorSnapshot;
import com.sora.aitravel.workflow.generate.DayContext;
import com.sora.aitravel.workflow.generate.PoiCandidate;
import com.sora.aitravel.workflow.generate.PoiIdentityService;
import com.sora.aitravel.workflow.generate.RouteAnchor;
import com.sora.aitravel.workflow.generate.RouteLegMetric;
import com.sora.aitravel.workflow.generate.RouteMatrix;
import com.sora.aitravel.workflow.generate.RouteOrderOptimizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Orders selected POIs with optional day start and stay anchors. */
@Component
@RequiredArgsConstructor
public class DayRouteOrderService {
    private static final String SOURCE_ESTIMATED = "ESTIMATED";

    private final RouteOrderOptimizer routeOrderOptimizer;
    private final PoiIdentityService poiIdentityService;

    public List<PoiCandidate> optimize(List<PoiCandidate> selected, DayContext dayContext) {
        if (selected == null || selected.size() < 2) {
            return selected;
        }
        if (selected.stream().anyMatch(candidate -> !hasRouteLocation(candidate))) {
            throw new IllegalStateException("景点候选缺少坐标，无法进行顺路排序");
        }
        List<RouteAnchor> spotAnchors = selected.stream().map(this::routeAnchor).toList();
        RouteAnchor fixedStart = snapshotAnchor(dayContext, dayContext.getSkeleton().getStartArea(), "DAY_START");
        RouteAnchor fixedEnd = snapshotAnchor(dayContext, dayContext.getSkeleton().getStayArea(), "STAY_AREA");
        List<RouteAnchor> ordered =
                fixedStart != null && fixedEnd != null
                        ? optimizeWithFixedAnchors(spotAnchors, fixedStart, fixedEnd)
                        : optimizeWithoutFixedAnchors(spotAnchors);
        return mapToCandidates(selected, ordered);
    }

    private List<RouteAnchor> optimizeWithFixedAnchors(
            List<RouteAnchor> spotAnchors, RouteAnchor fixedStart, RouteAnchor fixedEnd) {
        List<RouteAnchor> matrixAnchors = new ArrayList<>();
        matrixAnchors.add(fixedStart);
        matrixAnchors.addAll(spotAnchors);
        matrixAnchors.add(fixedEnd);
        RouteMatrix matrix = estimatedRouteMatrix(matrixAnchors);
        return routeOrderOptimizer.optimize(fixedStart, spotAnchors, fixedEnd, matrix);
    }

    private List<RouteAnchor> optimizeWithoutFixedAnchors(List<RouteAnchor> anchors) {
        RouteMatrix matrix = estimatedRouteMatrix(anchors);
        List<RouteAnchor> best = null;
        int bestCost = Integer.MAX_VALUE;
        for (RouteAnchor start : anchors) {
            for (RouteAnchor end : anchors) {
                if (start.getId().equals(end.getId())) {
                    continue;
                }
                List<RouteAnchor> middle = anchors.stream()
                        .filter(anchor ->
                                !anchor.getId().equals(start.getId()) && !anchor.getId().equals(end.getId()))
                        .toList();
                List<RouteAnchor> ordered = routeOrderOptimizer.optimize(start, middle, end, matrix);
                int cost = routeCost(ordered, matrix);
                if (cost < bestCost) {
                    bestCost = cost;
                    best = ordered;
                }
            }
        }
        if (best == null) {
            throw new IllegalStateException("顺路排序失败");
        }
        return best;
    }

    private List<PoiCandidate> mapToCandidates(List<PoiCandidate> selected, List<RouteAnchor> ordered) {
        LinkedHashMap<String, PoiCandidate> byKey = new LinkedHashMap<>();
        selected.forEach(candidate -> byKey.put(poiIdentityService.dedupKey(candidate), candidate));
        return ordered.stream()
                .map(anchor -> byKey.get(anchor.getId()))
                .filter(candidate -> candidate != null)
                .toList();
    }

    private RouteMatrix estimatedRouteMatrix(List<RouteAnchor> anchors) {
        RouteMatrix matrix = new RouteMatrix(anchors);
        for (RouteAnchor origin : anchors) {
            for (RouteAnchor destination : anchors) {
                if (origin.getId().equals(destination.getId())) {
                    continue;
                }
                int distanceMeters = GeoRouteCalculator.roadDistanceMeters(
                        origin.getLng(), origin.getLat(), destination.getLng(), destination.getLat());
                matrix.put(RouteLegMetric.builder()
                        .fromId(origin.getId())
                        .toId(destination.getId())
                        .distanceMeters(distanceMeters)
                        .durationSeconds(GeoRouteCalculator.drivingSeconds(distanceMeters))
                        .source(SOURCE_ESTIMATED)
                        .build());
            }
        }
        return matrix;
    }

    private RouteAnchor routeAnchor(PoiCandidate candidate) {
        double[] lngLat = GeoRouteCalculator.parseLocation(routeLocation(candidate));
        return RouteAnchor.builder()
                .id(poiIdentityService.dedupKey(candidate))
                .type("SCENIC")
                .title(candidate.getName())
                .city(candidate.getCity())
                .area(candidate.getArea())
                .address(candidate.getAddress())
                .lng(lngLat == null ? null : lngLat[0])
                .lat(lngLat == null ? null : lngLat[1])
                .sourceId(candidate.getSourcePoiId())
                .sourceType(candidate.getSource())
                .tags(candidate.getBusinessTags())
                .build();
    }

    private RouteAnchor snapshotAnchor(DayContext dayContext, AreaAnchorSnapshot snapshot, String type) {
        if (snapshot == null) {
            return null;
        }
        double[] lngLat = GeoRouteCalculator.parseLocation(snapshot.getLocation());
        if (lngLat == null) {
            return null;
        }
        return RouteAnchor.builder()
                .id(type + "_DAY_" + dayContext.getDay())
                .type(type)
                .title(snapshot.getName())
                .city(snapshot.getCity())
                .area(snapshot.getArea())
                .address(snapshot.getAddress())
                .lng(lngLat[0])
                .lat(lngLat[1])
                .sourceId(snapshot.getId())
                .sourceType("MACRO_ROUTE")
                .tags(List.of(type))
                .build();
    }

    private int routeCost(List<RouteAnchor> ordered, RouteMatrix matrix) {
        int cost = 0;
        for (int index = 0; index < ordered.size() - 1; index++) {
            cost += matrix.durationSeconds(ordered.get(index).getId(), ordered.get(index + 1).getId());
        }
        return cost;
    }

    private boolean hasRouteLocation(PoiCandidate candidate) {
        return GeoRouteCalculator.parseLocation(routeLocation(candidate)) != null;
    }

    private String routeLocation(PoiCandidate candidate) {
        return firstNonBlank(candidate.getEntranceLocation(), candidate.getLocation());
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
}
