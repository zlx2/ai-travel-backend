package com.sora.aitravel.service;

import com.sora.aitravel.model.PoiCandidate;

public interface PoiIdentityService {

    String dedupKey(PoiCandidate candidate);

    String normalizeName(String name);
}
