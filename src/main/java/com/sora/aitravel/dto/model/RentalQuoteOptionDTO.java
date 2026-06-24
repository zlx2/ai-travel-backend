package com.sora.aitravel.dto.model;

import java.util.Map;

public record RentalQuoteOptionDTO(
        String quoteId,
        String routeMode,
        String rentalCity,
        String citycode,
        String adcode,
        Long vehicleGroupId,
        String groupCode,
        String groupName,
        String displayName,
        String vehicleClass,
        String energyType,
        Integer seatsMin,
        Integer seatsMax,
        Long pickupPoiId,
        String pickupPoiName,
        String pickupAddress,
        Long returnPoiId,
        String returnPoiName,
        String returnAddress,
        String pickupMode,
        String returnMode,
        Integer rentalDays,
        Boolean isOneWay,
        Long priceTemplateId,
        RentalFeeBreakdownDTO feeBreakdown,
        Map<String, Object> priceSnapshot) {}
