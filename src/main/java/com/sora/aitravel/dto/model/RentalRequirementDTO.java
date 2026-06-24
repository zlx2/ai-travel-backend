package com.sora.aitravel.dto.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RentalRequirementDTO {

    private Boolean needRental;
    private String rentalStartCity;
    private String rentalEndCity;
    private String pickupMode;
    private String returnMode;
    private String pickupCity;
    private String returnCity;
    private String vehiclePreference;
    private Integer rentalDays;
    private Boolean deliveryRequired;
    private String deliveryAddress;
    private String returnAddress;
    private Boolean isOneWay;
}
