package com.sora.aitravel.service.impl;

import com.sora.aitravel.model.AreaAnchorSnapshot;
import com.sora.aitravel.model.DayContext;
import com.sora.aitravel.model.PoiCandidate;
import com.sora.aitravel.model.RouteAnchor;
import com.sora.aitravel.model.RouteLegMetric;
import com.sora.aitravel.service.route.GeoRouteCalculator;
import com.sora.aitravel.service.route.RouteMatrix;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 日间路线排序服务实现
 * 对选中的POI进行顺路排序，支持可选的日起点和停留锚点
 */
@Component
@RequiredArgsConstructor
public class DayRouteOrderServiceImpl {
    private static final String SOURCE_ESTIMATED = "ESTIMATED";

    private final RouteOrderOptimizerImpl routeOrderOptimizer;
    private final PoiIdentityServiceImpl poiIdentityService;

    /**
     * 优化POI顺序
     * 根据日上下文信息，对选中的POI候选进行顺路排序
     *
     * @param selected   选中的POI候选列表
     * @param dayContext 日上下文信息
     * @return 排序后的POI候选列表
     */
    public List<PoiCandidate> optimize(List<PoiCandidate> selected, DayContext dayContext) {
        if (selected == null || selected.size() < 2) {
            return selected;
        }
        if (selected.stream().anyMatch(candidate -> !hasRouteLocation(candidate))) {
            throw new IllegalStateException("景点候选缺少坐标，无法进行顺路排序");
        }
        List<RouteAnchor> spotAnchors = selected.stream().map(this::routeAnchor).toList();
        RouteAnchor fixedStart =
                snapshotAnchor(dayContext, dayContext.getSkeleton().getStartArea(), "DAY_START");
        RouteAnchor fixedEnd =
                snapshotAnchor(dayContext, dayContext.getSkeleton().getStayArea(), "STAY_AREA");
        List<RouteAnchor> ordered =
                fixedStart != null && fixedEnd != null
                        ? optimizeWithFixedAnchors(spotAnchors, fixedStart, fixedEnd)
                        : optimizeWithoutFixedAnchors(spotAnchors);
        return mapToCandidates(selected, ordered);
    }

    /**
     * 使用固定锚点优化路线顺序
     *
     * @param spotAnchors 景点锚点列表
     * @param fixedStart  固定起点锚点
     * @param fixedEnd    固定终点锚点
     * @return 优化后的锚点顺序
     */
    private List<RouteAnchor> optimizeWithFixedAnchors(
            List<RouteAnchor> spotAnchors, RouteAnchor fixedStart, RouteAnchor fixedEnd) {
        List<RouteAnchor> matrixAnchors = new ArrayList<>();
        matrixAnchors.add(fixedStart);
        matrixAnchors.addAll(spotAnchors);
        matrixAnchors.add(fixedEnd);
        RouteMatrix matrix = estimatedRouteMatrix(matrixAnchors);
        return routeOrderOptimizer.optimize(fixedStart, spotAnchors, fixedEnd, matrix);
    }

    /**
     * 不使用固定锚点优化路线顺序
     * 遍历所有可能的起点和终点组合，选择总成本最低的路线
     *
     * @param anchors 锚点列表
     * @return 优化后的锚点顺序
     */
    private List<RouteAnchor> optimizeWithoutFixedAnchors(List<RouteAnchor> anchors) {
        RouteMatrix matrix = estimatedRouteMatrix(anchors);
        List<RouteAnchor> best = null;
        int bestCost = Integer.MAX_VALUE;
        for (RouteAnchor start : anchors) {
            for (RouteAnchor end : anchors) {
                if (start.getId().equals(end.getId())) {
                    continue;
                }
                List<RouteAnchor> middle =
                        anchors.stream()
                                .filter(
                                        anchor ->
                                                !anchor.getId().equals(start.getId())
                                                        && !anchor.getId().equals(end.getId()))
                                .toList();
                List<RouteAnchor> ordered =
                        routeOrderOptimizer.optimize(start, middle, end, matrix);
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

    /**
     * 将排序后的RouteAnchor映射回PoiCandidate
     *
     * @param selected 原始POI候选列表
     * @param ordered  排序后的锚点列表
     * @return 排序后的POI候选列表
     */
    private List<PoiCandidate> mapToCandidates(
            List<PoiCandidate> selected, List<RouteAnchor> ordered) {
        LinkedHashMap<String, PoiCandidate> byKey = new LinkedHashMap<>();
        selected.forEach(candidate -> byKey.put(poiIdentityService.dedupKey(candidate), candidate));
        return ordered.stream()
                .map(anchor -> byKey.get(anchor.getId()))
                .filter(candidate -> candidate != null)
                .toList();
    }

    /**
     * 估算路线矩阵
     * 根据锚点坐标计算两两之间的距离和预计行驶时间
     *
     * @param anchors 锚点列表
     * @return 路线矩阵
     */
    private RouteMatrix estimatedRouteMatrix(List<RouteAnchor> anchors) {
        RouteMatrix matrix = new RouteMatrix(anchors);
        for (RouteAnchor origin : anchors) {
            for (RouteAnchor destination : anchors) {
                if (origin.getId().equals(destination.getId())) {
                    continue;
                }
                int distanceMeters =
                        GeoRouteCalculator.roadDistanceMeters(
                                origin.getLng(),
                                origin.getLat(),
                                destination.getLng(),
                                destination.getLat());
                matrix.put(
                        RouteLegMetric.builder()
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

    /**
     * 将PoiCandidate转换为RouteAnchor
     *
     * @param candidate POI候选
     * @return 路线锚点
     */
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

    /**
     * 创建快照锚点
     *
     * @param dayContext 日上下文
     * @param snapshot   区域锚点快照
     * @param type       锚点类型
     * @return 路线锚点，如果快照为空或无坐标则返回null
     */
    private RouteAnchor snapshotAnchor(
            DayContext dayContext, AreaAnchorSnapshot snapshot, String type) {
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

    /**
     * 计算路线总成本（秒）
     *
     * @param ordered 排序后的锚点列表
     * @param matrix  路线矩阵
     * @return 总行驶时间（秒）
     */
    private int routeCost(List<RouteAnchor> ordered, RouteMatrix matrix) {
        int cost = 0;
        for (int index = 0; index < ordered.size() - 1; index++) {
            cost +=
                    matrix.durationSeconds(
                            ordered.get(index).getId(), ordered.get(index + 1).getId());
        }
        return cost;
    }

    /**
     * 检查POI候选是否有可用的路线坐标
     *
     * @param candidate POI候选
     * @return 是否有路线坐标
     */
    private boolean hasRouteLocation(PoiCandidate candidate) {
        return GeoRouteCalculator.parseLocation(routeLocation(candidate)) != null;
    }

    /**
     * 获取POI候选的路线坐标
     * 优先使用入口坐标，其次使用普通坐标
     *
     * @param candidate POI候选
     * @return 坐标字符串
     */
    private String routeLocation(PoiCandidate candidate) {
        return firstNonBlank(candidate.getEntranceLocation(), candidate.getLocation());
    }

    /**
     * 获取第一个非空字符串
     *
     * @param first  第一个字符串
     * @param second 第二个字符串
     * @return 第一个非空字符串，如果都为空则返回second
     */
    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
}