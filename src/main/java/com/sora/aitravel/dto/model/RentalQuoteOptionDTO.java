package com.sora.aitravel.dto.model;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentalQuoteOptionDTO {

    private String quoteId;
    private String routeMode;
    private String rentalCity;
    private String citycode;
    private String adcode;
    private Long vehicleGroupId;
    private String groupCode;
    private String groupName;
    private String displayName;
    private String vehicleClass;
    private String energyType;
    private Integer seatsMin;
    private Integer seatsMax;
    private Long pickupPoiId;
    private String pickupPoiName;
    private String pickupAddress;
    private Long returnPoiId;
    private String returnPoiName;
    private String returnAddress;
    private String pickupMode;
    private String returnMode;
    private Integer rentalDays;
    private Boolean isOneWay;
    private Long priceTemplateId;
    private RentalFeeBreakdownDTO feeBreakdown;
    private Map<String, Object> priceSnapshot;
}
