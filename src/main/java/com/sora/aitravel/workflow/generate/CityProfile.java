package com.sora.aitravel.workflow.generate;

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

    String destination() {
        return destination;
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
}
