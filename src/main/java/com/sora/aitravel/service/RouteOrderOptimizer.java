package com.sora.aitravel.service;

import com.sora.aitravel.model.RouteAnchor;
import com.sora.aitravel.service.route.RouteMatrix;
import java.util.List;

public interface RouteOrderOptimizer {

    List<RouteAnchor> optimize(
            RouteAnchor start, List<RouteAnchor> middle, RouteAnchor end, RouteMatrix matrix);
}
