package com.sora.aitravel.service;

import com.sora.aitravel.model.trip.generate.PoiCandidate;
import java.util.List;

public interface PoiClusterer {

    List<PoiCandidate> bestCluster(List<PoiCandidate> candidates, int limit, double maxKm);

    double totalDirectRouteKm(List<PoiCandidate> candidates);

    boolean fitsCluster(List<PoiCandidate> selected, PoiCandidate candidate, double maxKm);

    double directDistanceKm(PoiCandidate first, PoiCandidate second);
}
