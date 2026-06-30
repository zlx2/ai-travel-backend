package com.sora.aitravel.workflow.generate;

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

    String mode() {
        return mode;
    }

    String durationEstimate() {
        return durationEstimate;
    }
}
