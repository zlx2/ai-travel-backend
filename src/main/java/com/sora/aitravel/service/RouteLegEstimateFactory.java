package com.sora.aitravel.service;

import com.sora.aitravel.dto.model.TripPlanDTO;
import java.util.List;

public interface RouteLegEstimateFactory {

    List<TripPlanDTO.RouteLeg> build(List<TripPlanDTO.Spot> spots, boolean rentalEnabled);
}
