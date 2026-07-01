package com.sora.aitravel.model.trip.generate;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MacroRouteFact {
    private String planId;
    private List<MacroRouteDayFact> dayFacts;
    private Integer totalDrivingMinutes;
    private Integer totalDistanceMeters;
    private List<String> backtrackingSignals;
}
