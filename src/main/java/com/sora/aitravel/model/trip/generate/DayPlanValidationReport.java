package com.sora.aitravel.model.trip.generate;

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

    public Integer day() {
        return day;
    }

    public Boolean passed() {
        return passed;
    }

    public List<String> warnings() {
        return warnings;
    }
}
