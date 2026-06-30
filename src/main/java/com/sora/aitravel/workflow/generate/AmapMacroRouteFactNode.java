package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Adds AMap driving facts to each AI-proposed macro route plan. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AmapMacroRouteFactNode {
    private final RouteMatrixService routeMatrixService;

    public void execute(GenerateWorkflowContext context) {
        if (context.getMacroRoutePlans() == null || context.getMacroRoutePlans().isEmpty()) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "缺少路线骨架候选，无法补充高德事实");
        }
        Map<String, AreaAnchorCandidate> anchors = anchorMap(context.getCandidatePool());
        context.getMacroRoutePlans()
                .forEach(plan -> AreaAnchorResolver.canonicalizePlan(plan, anchors));
        List<MacroRouteFact> facts = context.getMacroRoutePlans().stream()
                .map(plan -> fact(plan, anchors))
                .toList();
        context.setMacroRouteFacts(facts);
        log.info("节点[amap-macro-route-fact]：已补充宏观路线事实，plans={}", facts.size());
    }

    private MacroRouteFact fact(MacroRoutePlan plan, Map<String, AreaAnchorCandidate> anchors) {
        List<MacroRouteDayFact> dayFacts = new ArrayList<>();
        List<String> backtrackingSignals = new ArrayList<>();
        int totalDistance = 0;
        int totalMinutes = 0;
        List<String> previousFocus = new ArrayList<>();
        for (MacroRouteDay day : plan.getDays()) {
            List<String> route = macroRouteIds(day);
            List<RouteLegMetric> metrics = routeMatrixService.buildDrivingRouteMetrics(toRouteAnchors(route, anchors));
            int distance = metrics.stream().mapToInt(RouteLegMetric::getDistanceMeters).sum();
            int seconds = metrics.stream().mapToInt(RouteLegMetric::getDurationSeconds).sum();
            totalDistance += distance;
            int minutes = (int) Math.ceil(seconds / 60.0);
            totalMinutes += minutes;
            dayFacts.add(new MacroRouteDayFact(
                    day.getDay(),
                    minutes,
                    distance,
                    route.stream().map(id -> nameOf(anchors, id)).reduce((a, b) -> a + " -> " + b).orElse("")));
            for (String focus : safe(day.getFocusAreaIds())) {
                if (previousFocus.contains(focus)) {
                    backtrackingSignals.add("Day " + day.getDay() + " 回到已访问区域：" + nameOf(anchors, focus));
                }
            }
            previousFocus.addAll(safe(day.getFocusAreaIds()));
        }
        return new MacroRouteFact(plan.getId(), dayFacts, totalMinutes, totalDistance, backtrackingSignals);
    }

    private List<RouteAnchor> toRouteAnchors(List<String> route, Map<String, AreaAnchorCandidate> anchors) {
        return route.stream()
                .map(id -> AreaAnchorResolver.resolve(anchors, id, "routeAreaId"))
                .map(this::toRouteAnchor)
                .toList();
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

    private RouteAnchor toRouteAnchor(AreaAnchorCandidate anchor) {
        String[] parts = anchor.getLocation().split(",");
        return RouteAnchor.builder()
                .id(anchor.getId())
                .type(anchor.getRole())
                .title(anchor.getName())
                .city(anchor.getCity())
                .area(anchor.getArea())
                .address(anchor.getAddress())
                .lng(Double.valueOf(parts[0]))
                .lat(Double.valueOf(parts[1]))
                .sourceId(anchor.getSourcePoiId())
                .sourceType(anchor.getSource())
                .tags(anchor.getTags())
                .build();
    }

    private String nameOf(Map<String, AreaAnchorCandidate> anchors, String id) {
        AreaAnchorCandidate anchor = anchors.get(id);
        return anchor == null ? id : anchor.getName();
    }

    private List<String> safe(List<String> values) {
        return values == null ? List.of() : values;
    }
}
