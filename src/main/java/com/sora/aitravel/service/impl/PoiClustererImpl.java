package com.sora.aitravel.service.impl;

import com.sora.aitravel.model.trip.generate.PoiCandidate;
import com.sora.aitravel.service.PoiClusterer;
import com.sora.aitravel.service.route.GeoRouteCalculator;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Selects compact POI groups from coordinate-bearing candidates. */
@Component
public class PoiClustererImpl implements PoiClusterer {
    @Override
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

    @Override
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

    @Override
    public boolean fitsCluster(List<PoiCandidate> selected, PoiCandidate candidate, double maxKm) {
        if (selected == null || selected.isEmpty()) {
            return true;
        }
        return selected.stream().allMatch(item -> directDistanceKm(item, candidate) <= maxKm);
    }

    @Override
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

    private String routeLocation(PoiCandidate candidate) {
        return firstNonBlank(candidate.getEntranceLocation(), candidate.getLocation());
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
}
