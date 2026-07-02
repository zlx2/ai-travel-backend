package com.sora.aitravel.service;

import com.sora.aitravel.model.AreaAnchorCandidate;
import com.sora.aitravel.model.PoiCandidate;
import java.util.List;

public interface TravelKnowledgeService {
    List<String> supportedCities(List<String> requestedCities);

    List<PoiCandidate> scenicCandidates(List<String> requestedCities);

    List<AreaAnchorCandidate> areaAnchors(List<String> requestedCities);

    void cacheAmapScenicCandidates(String cityName, List<PoiCandidate> candidates);
}
