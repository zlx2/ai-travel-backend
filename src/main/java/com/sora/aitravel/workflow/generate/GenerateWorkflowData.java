package com.sora.aitravel.workflow.generate;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
class DaySkeleton {
    private Integer day;
    private String theme;
    private String targetArea;
    private String intensity;

    Integer day() {
        return day;
    }

    String theme() {
        return theme;
    }

    String targetArea() {
        return targetArea;
    }
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class CityProfile {
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

@Data
@NoArgsConstructor
@AllArgsConstructor
class DayContext {
    private Integer day;
    private DaySkeleton skeleton;
    private List<String> usedPlaces;
    private String hotelArea;
    private String pace;

    Integer day() {
        return day;
    }

    DaySkeleton skeleton() {
        return skeleton;
    }

    String hotelArea() {
        return hotelArea;
    }
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class DayQueryPlan {
    private Integer day;
    private List<QueryItem> queries;

    Integer day() {
        return day;
    }

    List<QueryItem> queries() {
        return queries;
    }
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class QueryItem {
    private String type;
    private String keyword;
    private String city;
    private String around;
    private String from;
    private String to;
    private String purpose;

    String type() {
        return type;
    }

    String keyword() {
        return keyword;
    }

    String around() {
        return around;
    }

    String from() {
        return from;
    }

    String to() {
        return to;
    }
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class PoiCandidate {
    private String category;
    private String name;
    private String address;
    private String area;
    private String location;
    private String source;
    private String sourcePoiId;
    private String reason;
    private Integer distanceMeters;

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

@Data
@NoArgsConstructor
@AllArgsConstructor
class TransportRoute {
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

@Data
@NoArgsConstructor
@AllArgsConstructor
class DayDataPackage {
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

@Data
@NoArgsConstructor
@AllArgsConstructor
class DayPlanValidationReport {
    private Integer day;
    private Boolean passed;
    private List<String> warnings;

    Integer day() {
        return day;
    }

    Boolean passed() {
        return passed;
    }

    List<String> warnings() {
        return warnings;
    }
}
