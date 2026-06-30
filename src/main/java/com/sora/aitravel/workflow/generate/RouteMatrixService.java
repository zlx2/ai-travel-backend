package com.sora.aitravel.workflow.generate;

import java.util.List;

public interface RouteMatrixService {
    RouteMatrix buildDrivingMatrix(List<RouteAnchor> anchors);

    List<RouteLegMetric> buildDrivingRouteMetrics(List<RouteAnchor> orderedAnchors);
}
