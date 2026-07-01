package com.sora.aitravel.service;

import com.sora.aitravel.model.RouteAnchor;
import com.sora.aitravel.model.RouteLegMetric;
import com.sora.aitravel.service.route.RouteMatrix;
import java.util.List;

public interface RouteMatrixService {
    RouteMatrix buildDrivingMatrix(List<RouteAnchor> anchors);

    List<RouteLegMetric> buildDrivingRouteMetrics(List<RouteAnchor> orderedAnchors);
}
