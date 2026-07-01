package com.sora.aitravel.workflow.generate;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DayDataPackage {
    private Integer day;
    private List<PoiCandidate> scenicCandidates;
    private List<PoiCandidate> foodCandidates;
    private List<PoiCandidate> hotelCandidates;
    private List<TransportRoute> transportRoutes;

    Integer day() {
        return day;
    }

    List<PoiCandidate> scenicCandidates() {
        return scenicCandidates;
    }

    List<PoiCandidate> foodCandidates() {
        return foodCandidates;
    }

    List<PoiCandidate> hotelCandidates() {
        return hotelCandidates;
    }

    List<TransportRoute> transportRoutes() {
        return transportRoutes;
    }
}
