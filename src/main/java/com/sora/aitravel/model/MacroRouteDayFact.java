package com.sora.aitravel.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MacroRouteDayFact {
    private Integer day;
    private Integer drivingMinutes;
    private Integer distanceMeters;
    private String summary;
}
