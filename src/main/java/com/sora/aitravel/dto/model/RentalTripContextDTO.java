package com.sora.aitravel.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentalTripContextDTO {

    private RentalArrivalPointDTO arrivalPoint;
    private RentalStoreDTO matchedStore;
    private RentalPickupPlanDTO pickupPlan;
    private String arrivalMode;
    private String arrivalTimeRange;
    private String routeStructure;
    private String dailyDrivingLimit;
    private String returnMode;
    private String returnPoint;
    private Boolean withElderOrChildren;
    private String luggageLevel;
}
