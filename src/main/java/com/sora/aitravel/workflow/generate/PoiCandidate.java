package com.sora.aitravel.workflow.generate;

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

    String name() {
        return name;
    }

    String area() {
        return area;
    }

    String source() {
        return source;
    }

    String reason() {
        return reason;
    }
}
