package com.sora.aitravel.service;

import com.sora.aitravel.model.trip.generate.PoiCandidate;

public interface PoiIdentityService {

    String dedupKey(PoiCandidate candidate);

    String normalizeName(String name);
}
