package com.sora.aitravel.service.impl;

import com.sora.aitravel.model.PoiCandidate;
import com.sora.aitravel.service.route.GeoRouteCalculator;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * POI聚类器实现
 * 从带坐标的候选POI中选择紧凑的POI组
 */
@Component
public class PoiClustererImpl {

    /**
     * 选择最优的POI集群
     * 遍历每个候选POI作为锚点，找出距离锚点不超过maxKm的POI组成集群
     *
     * @param candidates POI候选列表
     * @param limit      集群最大数量限制
     * @param maxKm      最大距离（公里）
     * @return 最紧凑的POI集群
     */
    public List<PoiCandidate> bestCluster(List<PoiCandidate> candidates, int limit, double maxKm) {
        if (candidates == null || candidates.isEmpty() || limit <= 0) {
            return List.of();
        }
        List<PoiCandidate> best = List.of();
        for (PoiCandidate anchor : candidates) {
            double[] anchorLocation = GeoRouteCalculator.parseLocation(anchor.getLocation());
            if (anchorLocation == null) {
                continue;
            }
            List<PoiCandidate> cluster = new ArrayList<>();
            for (PoiCandidate candidate : candidates) {
                double[] location = GeoRouteCalculator.parseLocation(candidate.getLocation());
                if (location == null) {
                    continue;
                }
                if (GeoRouteCalculator.distanceKm(
                                anchorLocation[0], anchorLocation[1], location[0], location[1])
                        <= maxKm) {
                    cluster.add(candidate);
                }
                if (cluster.size() >= limit) {
                    break;
                }
            }
            if (cluster.size() > best.size()) {
                best = cluster;
            }
            if (best.size() >= limit) {
                return best;
            }
        }
        return best;
    }

    /**
     * 计算连续POI之间的总直线距离
     *
     * @param candidates POI候选列表
     * @return 总距离（公里）
     */
    public double totalDirectRouteKm(List<PoiCandidate> candidates) {
        if (candidates == null || candidates.size() < 2) {
            return 0;
        }
        double total = 0;
        for (int index = 0; index < candidates.size() - 1; index++) {
            total += directDistanceKm(candidates.get(index), candidates.get(index + 1));
        }
        return total;
    }

    /**
     * 判断候选POI是否适合加入现有集群
     *
     * @param selected  已选择的POI列表
     * @param candidate 待判断的候选POI
     * @param maxKm     最大距离（公里）
     * @return 是否适合加入集群
     */
    public boolean fitsCluster(List<PoiCandidate> selected, PoiCandidate candidate, double maxKm) {
        if (selected == null || selected.isEmpty()) {
            return true;
        }
        return selected.stream().allMatch(item -> directDistanceKm(item, candidate) <= maxKm);
    }

    /**
     * 计算两个POI之间的直线距离
     *
     * @param first  第一个POI
     * @param second 第二个POI
     * @return 直线距离（公里），如果无法计算则返回1000
     */
    public double directDistanceKm(PoiCandidate first, PoiCandidate second) {
        if (first == null || second == null) {
            return 1000;
        }
        double[] from = GeoRouteCalculator.parseLocation(routeLocation(first));
        double[] to = GeoRouteCalculator.parseLocation(routeLocation(second));
        if (from == null || to == null) {
            return 1000;
        }
        return GeoRouteCalculator.distanceKm(from[0], from[1], to[0], to[1]);
    }

    /**
     * 获取POI的路线坐标
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