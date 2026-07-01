package com.sora.aitravel.service.route;

import com.sora.aitravel.model.trip.generate.RouteAnchor;
import com.sora.aitravel.model.trip.generate.RouteLegMetric;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** In-memory route cost matrix for a single day generation. */
public class RouteMatrix {
    private final Map<String, RouteAnchor> anchors = new HashMap<>();
    private final Map<String, RouteLegMetric> legs = new HashMap<>();

    public RouteMatrix(List<RouteAnchor> anchors) {
        anchors.forEach(anchor -> this.anchors.put(anchor.getId(), anchor));
    }

    public void put(RouteLegMetric leg) {
        legs.put(key(leg.getFromId(), leg.getToId()), leg);
    }

    public RouteAnchor anchor(String id) {
        return anchors.get(id);
    }

    public RouteLegMetric leg(String fromId, String toId) {
        return legs.get(key(fromId, toId));
    }

    public int durationSeconds(String fromId, String toId) {
        RouteLegMetric leg = leg(fromId, toId);
        return leg == null ? Integer.MAX_VALUE / 4 : leg.getDurationSeconds();
    }

    public int distanceMeters(String fromId, String toId) {
        RouteLegMetric leg = leg(fromId, toId);
        return leg == null ? Integer.MAX_VALUE / 4 : leg.getDistanceMeters();
    }

    private String key(String fromId, String toId) {
        return fromId + "->" + toId;
    }
}
