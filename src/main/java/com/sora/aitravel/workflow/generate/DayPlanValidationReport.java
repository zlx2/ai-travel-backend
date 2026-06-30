package com.sora.aitravel.workflow.generate;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DayPlanValidationReport {
    private Integer day;
    private Boolean passed;
    private List<String> warnings;

    Integer day() {
        return day;
    }

    Boolean passed() {
        return passed;
    }

    List<String> warnings() {
        return warnings;
    }
}
