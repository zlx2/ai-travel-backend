package com.sora.aitravel.model;

import lombok.Builder;
import lombok.Data;

/** Driving distance and duration between two anchors. */
@Data
@Builder
public class RouteLegMetric {
    private String fromId;
    private String toId;
    private int distanceMeters;
    private int durationSeconds;
    private String source;
}
