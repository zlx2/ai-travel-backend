package com.sora.aitravel.model;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/** A map-backed point or area used by route ordering. */
@Data
@Builder
public class RouteAnchor {
    private String id;
    private String type;
    private String title;
    private String city;
    private String area;
    private String address;
    private Double lng;
    private Double lat;
    private String sourceId;
    private String sourceType;
    private List<String> tags;

    public String location() {
        if (lng == null || lat == null) {
            return null;
        }
        return lng + "," + lat;
    }

    public boolean hasLocation() {
        return lng != null && lat != null;
    }
}
