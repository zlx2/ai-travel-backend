package com.sora.aitravel.dto.model;

import java.math.BigDecimal;
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
    private String recommendedPeople;
    private String recommendedLuggage;
    private String travelTags;
    private String exampleModels;
    private String description;
    private String iconUrl;
    private Long vehicleModelId;
    private String brand;
    private String series;
    private String seriesFullName;
    private Integer modelYear;
    private String bodyType;
    private String transmission;
    private Integer seats;
    private String imageUrl;
    private String summary;
    private String featureTags;
    private Long pickupPoiId;
    private String pickupPoiName;
    private String pickupAddress;
    private BigDecimal pickupLng;
    private BigDecimal pickupLat;
    private Long returnPoiId;
    private String returnPoiName;
    private String returnAddress;
    private BigDecimal returnLng;
    private BigDecimal returnLat;
    private String pickupMode;
    private String returnMode;
    private Integer rentalDays;
    private Boolean isOneWay;
    private Long priceTemplateId;
    private Integer availableCount;
    private Integer dailyMileageLimitKm;
    private Integer extraMileageFeeCent;
    private String includedServices;
    private RentalFeeBreakdownDTO feeBreakdown;
    private Map<String, Object> priceSnapshot;
}
