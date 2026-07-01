package com.sora.aitravel.model.trip.generate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransportRoute {
    private String from;
    private String to;
    private String mode;
    private String durationEstimate;
    private String distanceEstimate;
    private String source;
    private Boolean estimated;

    public String mode() {
        return mode;
    }

    public String durationEstimate() {
        return durationEstimate;
    }
}
