package com.sora.aitravel.dto.model;

public record RentalRequirementDTO(
        Boolean needRental,
        String rentalStartCity,
        String rentalEndCity,
        String pickupMode,
        String returnMode,
        String pickupCity,
        String returnCity,
        String vehiclePreference,
        Integer rentalDays,
        Boolean deliveryRequired,
        String deliveryAddress,
        String returnAddress,
        Boolean isOneWay) {}
