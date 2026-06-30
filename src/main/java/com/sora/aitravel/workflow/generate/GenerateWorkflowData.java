package com.sora.aitravel.workflow.generate;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
class DaySkeleton {
    private Integer day;
    private String theme;
    private String targetArea;
    private String intensity;
    private String startAreaId;
    private String focusAreaId;
    private String endAreaId;
    private String stayAreaId;
    private AreaAnchorSnapshot startArea;
    private AreaAnchorSnapshot focusArea;
    private AreaAnchorSnapshot endArea;
    private AreaAnchorSnapshot stayArea;

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
class AreaAnchorSnapshot {
    private String id;
    private String name;
    private String role;
    private String city;
    private String area;
    private String address;
    private String location;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class AreaAnchorCandidate {
    private String id;
    private String name;
    private String role;
    private String city;
    private String area;
    private String address;
    private String location;
    private String source;
    private String sourcePoiId;
    private List<String> tags;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class CandidatePool {
    private List<PoiCandidate> scenicCandidates;
    private List<AreaAnchorCandidate> areaAnchors;
    private AreaAnchorCandidate pickupAnchor;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class MacroRoutePlan {
    private String id;
    private String routeShape;
    private List<MacroRouteDay> days;
    private List<String> warnings;
    private String reason;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class MacroRouteDay {
    private Integer day;
    private String startAreaId;
    private List<String> focusAreaIds;
    private String endAreaId;
    private String stayAreaId;
    private String theme;
    private String reason;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class MacroRouteFact {
    private String planId;
    private List<MacroRouteDayFact> dayFacts;
    private Integer totalDrivingMinutes;
    private Integer totalDistanceMeters;
    private List<String> backtrackingSignals;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class MacroRouteDayFact {
    private Integer day;
    private Integer drivingMinutes;
    private Integer distanceMeters;
    private String summary;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class RouteCriticResult {
    private String selectedPlanId;
    private MacroRoutePlan revisedPlan;
    private Integer score;
    private List<String> warnings;
    private String reason;
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
    private Boolean rentalEnabled;
    private String rentalInstruction;
    private String routeStructure;
    private String dailyDrivingLimit;
    private String revisionText;

    Integer day() {
        return day;
    }

    DaySkeleton skeleton() {
        return skeleton;
    }

    String hotelArea() {
        return hotelArea;
    }

    boolean rentalEnabled() {
        return Boolean.TRUE.equals(rentalEnabled);
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
