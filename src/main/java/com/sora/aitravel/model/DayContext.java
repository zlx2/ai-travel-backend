package com.sora.aitravel.model;

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

    public Integer day() {
        return day;
    }

    public DaySkeleton skeleton() {
        return skeleton;
    }

    public String hotelArea() {
        return hotelArea;
    }

    public boolean rentalEnabled() {
        return Boolean.TRUE.equals(rentalEnabled);
    }
}
