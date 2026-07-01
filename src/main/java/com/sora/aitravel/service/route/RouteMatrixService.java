package com.sora.aitravel.service.route;

import com.sora.aitravel.workflow.generate.RouteAnchor;
import com.sora.aitravel.workflow.generate.RouteLegMetric;
import java.util.List;

public interface RouteMatrixService {
    RouteMatrix buildDrivingMatrix(List<RouteAnchor> anchors);

    List<RouteLegMetric> buildDrivingRouteMetrics(List<RouteAnchor> orderedAnchors);
}
