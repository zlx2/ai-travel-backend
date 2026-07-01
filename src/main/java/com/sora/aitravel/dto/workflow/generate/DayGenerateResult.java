package com.sora.aitravel.dto.workflow.generate;

import com.sora.aitravel.dto.model.TripPlanDTO;
import com.sora.aitravel.model.trip.generate.DayPlanValidationReport;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DayGenerateResult {
    private TripPlanDTO.DailyPlan dailyPlan;
    private List<TripPlanDTO.DailyPlan> lockedDailyPlans;
    private List<DayPlanValidationReport> validationReports;
}
