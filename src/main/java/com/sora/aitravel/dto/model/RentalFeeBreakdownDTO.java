package com.sora.aitravel.dto.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RentalFeeBreakdownDTO {

    private Integer rentalFeeCent;
    private Integer baseServiceFeeCent;
    private Integer vehiclePrepareFeeCent;
    private Integer oneWayFeeCent;
    private Integer deliveryFeeCent;
    private Integer totalPriceCent;
    private Integer rentalDepositCent;
    private Integer violationDepositCent;
    private Integer depositFreeThresholdScore;
}
