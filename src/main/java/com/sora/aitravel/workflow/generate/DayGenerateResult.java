package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.dto.model.TripPlanDTO;
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
