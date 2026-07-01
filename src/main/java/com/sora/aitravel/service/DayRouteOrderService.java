package com.sora.aitravel.service;

import com.sora.aitravel.model.DayContext;
import com.sora.aitravel.model.PoiCandidate;
import java.util.List;

public interface DayRouteOrderService {

    List<PoiCandidate> optimize(List<PoiCandidate> selected, DayContext dayContext);
}
