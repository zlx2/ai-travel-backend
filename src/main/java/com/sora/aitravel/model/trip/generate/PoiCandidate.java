package com.sora.aitravel.model.trip.generate;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** POI candidate used by trip generation workflows. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PoiCandidate {
    private String category;
    private String name;
    private String address;
    private String area;
    private String city;
    private String location;
    private String source;
    private String sourcePoiId;
    private String reason;
    private Integer distanceMeters;
    private String typeCode;
    private String parentPoiId;
    private String openingHours;
    private String rating;
    private Integer averageCost;
    private String businessArea;
    private List<String> businessTags;
    private String entranceLocation;
    private List<String> imageUrls;

    public String name() {
        return name;
    }

    public String area() {
        return area;
    }

    public String source() {
        return source;
    }

    public String reason() {
        return reason;
    }
}
