package com.sora.aitravel.model.trip.generate;

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

    public Integer day() {
        return day;
    }

    public List<PoiCandidate> scenicCandidates() {
        return scenicCandidates;
    }

    public List<PoiCandidate> foodCandidates() {
        return foodCandidates;
    }

    public List<PoiCandidate> hotelCandidates() {
        return hotelCandidates;
    }

    public List<TransportRoute> transportRoutes() {
        return transportRoutes;
    }
}
