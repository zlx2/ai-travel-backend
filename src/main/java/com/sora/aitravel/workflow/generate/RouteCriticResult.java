package com.sora.aitravel.workflow.generate;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteCriticResult {
    private String selectedPlanId;
    private MacroRoutePlan revisedPlan;
    private Integer score;
    private List<String> warnings;
    private String reason;
}
