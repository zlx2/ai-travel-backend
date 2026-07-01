package com.sora.aitravel.workflow.generate;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Per-day context used by day generation workflow nodes. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DayContext {
    private Integer day;
    private DaySkeleton skeleton;
    private List<String> usedPlaces;
    private String hotelArea;
    private String pace;
    private Boolean rentalEnabled;
    private String rentalInstruction;
    private String routeStructure;
    private String dailyDrivingLimit;
    private String revisionText;

    Integer day() {
        return day;
    }

    DaySkeleton skeleton() {
        return skeleton;
    }

    String hotelArea() {
        return hotelArea;
    }

    boolean rentalEnabled() {
        return Boolean.TRUE.equals(rentalEnabled);
    }
}
