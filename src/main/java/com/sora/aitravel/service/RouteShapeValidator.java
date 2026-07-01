package com.sora.aitravel.service;

import com.sora.aitravel.dto.model.TripPlanDTO;
import java.util.List;

public interface RouteShapeValidator {

    List<String> validate(TripPlanDTO.DailyPlan dailyPlan, boolean rentalEnabled);
}
