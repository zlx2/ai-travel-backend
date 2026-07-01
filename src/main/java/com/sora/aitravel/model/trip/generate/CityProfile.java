package com.sora.aitravel.model.trip.generate;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CityProfile {
    private String destination;
    private List<String> popularAreas;
    private List<String> transportHubs;
    private List<PoiCandidate> scenicCandidates;
    private List<PoiCandidate> foodCandidates;
    private List<PoiCandidate> hotelCandidates;

    public String destination() {
        return destination;
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
}
