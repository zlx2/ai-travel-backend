package com.sora.aitravel.workflow.generate.prepare;

import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.CANDIDATE_POOL;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.MACRO_ROUTE_FACTS;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.MACRO_ROUTE_PLANS;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.model.trip.generate.AreaAnchorCandidate;
import com.sora.aitravel.model.trip.generate.CandidatePool;
import com.sora.aitravel.model.trip.generate.MacroRouteDay;
import com.sora.aitravel.model.trip.generate.MacroRouteDayFact;
import com.sora.aitravel.model.trip.generate.MacroRouteFact;
import com.sora.aitravel.model.trip.generate.MacroRoutePlan;
import com.sora.aitravel.model.trip.generate.RouteLegMetric;
import com.sora.aitravel.service.route.GeoRouteCalculator;
import com.sora.aitravel.workflow.generate.state.TripGraphStateCodec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Adds AMap driving facts to each AI-proposed macro route plan. */
@Slf4j
@Component
public class AmapMacroRouteFactNode {

    public Map<String, Object> execute(OverAllState state) {
        List<MacroRouteFact> facts =
                buildFacts(
                        TripGraphStateCodec.optionalList(
                                state, MACRO_ROUTE_PLANS, MacroRoutePlan.class),
                        TripGraphStateCodec.required(state, CANDIDATE_POOL, CandidatePool.class));
        return TripGraphStateCodec.patch(MACRO_ROUTE_FACTS, facts);
    }

    private List<MacroRouteFact> buildFacts(
            List<MacroRoutePlan> macroRoutePlans, CandidatePool candidatePool) {
        if (macroRoutePlans == null || macroRoutePlans.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "缺少路线骨架候选，无法补充高德事实");
        }
        Map<String, AreaAnchorCandidate> anchors = anchorMap(candidatePool);
        macroRoutePlans.forEach(plan -> AreaAnchorResolver.canonicalizePlan(plan, anchors));
        List<MacroRouteFact> facts =
                macroRoutePlans.stream().map(plan -> fact(plan, anchors)).toList();
        log.info("节点[amap-macro-route-fact]：已补充宏观路线事实，plans={}", facts.size());
        return facts;
    }

    private MacroRouteFact fact(MacroRoutePlan plan, Map<String, AreaAnchorCandidate> anchors) {
        List<MacroRouteDayFact> dayFacts = new ArrayList<>();
        List<String> backtrackingSignals = new ArrayList<>();
        int totalDistance = 0;
        int totalMinutes = 0;
        List<String> previousFocus = new ArrayList<>();
        for (MacroRouteDay day : plan.getDays()) {
            List<String> route = macroRouteIds(day);
            List<RouteLegMetric> metrics = estimatedRouteMetrics(route, anchors);
            int distance = metrics.stream().mapToInt(RouteLegMetric::getDistanceMeters).sum();
            int seconds = metrics.stream().mapToInt(RouteLegMetric::getDurationSeconds).sum();
            totalDistance += distance;
            int minutes = (int) Math.ceil(seconds / 60.0);
            totalMinutes += minutes;
            dayFacts.add(
                    new MacroRouteDayFact(
                            day.getDay(),
                            minutes,
                            distance,
                            route.stream()
                                    .map(id -> nameOf(anchors, id))
                                    .reduce((a, b) -> a + " -> " + b)
                                    .orElse("")));
            for (String focus : safe(day.getFocusAreaIds())) {
                if (previousFocus.contains(focus)) {
                    backtrackingSignals.add(
                            "Day " + day.getDay() + " 回到已访问区域：" + nameOf(anchors, focus));
                }
            }
            previousFocus.addAll(safe(day.getFocusAreaIds()));
        }
        return new MacroRouteFact(
                plan.getId(), dayFacts, totalMinutes, totalDistance, backtrackingSignals);
    }

    private List<RouteLegMetric> estimatedRouteMetrics(
            List<String> route, Map<String, AreaAnchorCandidate> anchors) {
        List<RouteLegMetric> metrics = new ArrayList<>();
        for (int index = 0; index < route.size() - 1; index++) {
            AreaAnchorCandidate from =
                    AreaAnchorResolver.resolve(anchors, route.get(index), "routeAreaId");
            AreaAnchorCandidate to =
                    AreaAnchorResolver.resolve(anchors, route.get(index + 1), "routeAreaId");
            int distanceMeters = estimateRoadDistanceMeters(from, to);
            metrics.add(
                    RouteLegMetric.builder()
                            .fromId(from.getId())
                            .toId(to.getId())
                            .distanceMeters(distanceMeters)
                            .durationSeconds(estimateDrivingSeconds(distanceMeters))
                            .source("ESTIMATED_COORDINATE")
                            .build());
        }
        return metrics;
    }

    private List<String> macroRouteIds(MacroRouteDay day) {
        List<String> ids = new ArrayList<>();
        add(ids, day.getStartAreaId());
        for (String focus : safe(day.getFocusAreaIds())) {
            add(ids, focus);
        }
        add(ids, day.getEndAreaId());
        add(ids, day.getStayAreaId());
        return ids;
    }

    private void add(List<String> ids, String id) {
        if (id != null && !id.isBlank() && (ids.isEmpty() || !ids.get(ids.size() - 1).equals(id))) {
            ids.add(id);
        }
    }

    private Map<String, AreaAnchorCandidate> anchorMap(CandidatePool pool) {
        if (pool == null || pool.getAreaAnchors() == null || pool.getAreaAnchors().isEmpty()) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "缺少区域锚点，无法计算宏观路线");
        }
        Map<String, AreaAnchorCandidate> map = new LinkedHashMap<>();
        pool.getAreaAnchors().forEach(anchor -> map.put(anchor.getId(), anchor));
        return map;
    }

    private int estimateRoadDistanceMeters(AreaAnchorCandidate from, AreaAnchorCandidate to) {
        double[] fromLocation = GeoRouteCalculator.parseLocation(from.getLocation());
        double[] toLocation = GeoRouteCalculator.parseLocation(to.getLocation());
        if (fromLocation == null || toLocation == null) {
            return 0;
        }
        return GeoRouteCalculator.roadDistanceMeters(
                fromLocation[0], fromLocation[1], toLocation[0], toLocation[1]);
    }

    private int estimateDrivingSeconds(int distanceMeters) {
        return GeoRouteCalculator.drivingSeconds(distanceMeters);
    }

    private String nameOf(Map<String, AreaAnchorCandidate> anchors, String id) {
        AreaAnchorCandidate anchor = anchors.get(id);
        return anchor == null ? id : anchor.getName();
    }

    private List<String> safe(List<String> values) {
        return values == null ? List.of() : values;
    }
}
