package com.sora.aitravel.service;

import com.sora.aitravel.model.trip.generate.DayContext;
import com.sora.aitravel.model.trip.generate.PoiCandidate;
import java.util.List;

public interface DayRouteOrderService {

    List<PoiCandidate> optimize(List<PoiCandidate> selected, DayContext dayContext);
}
