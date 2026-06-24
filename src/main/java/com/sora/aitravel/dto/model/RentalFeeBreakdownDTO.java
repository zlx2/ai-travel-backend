package com.sora.aitravel.dto.model;

public record RentalFeeBreakdownDTO(
        Integer rentalFeeCent,
        Integer baseServiceFeeCent,
        Integer vehiclePrepareFeeCent,
        Integer oneWayFeeCent,
        Integer deliveryFeeCent,
        Integer totalPriceCent,
        Integer rentalDepositCent,
        Integer violationDepositCent,
        Integer depositFreeThresholdScore) {}
