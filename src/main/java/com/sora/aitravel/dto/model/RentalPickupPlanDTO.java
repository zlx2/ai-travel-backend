package com.sora.aitravel.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentalPickupPlanDTO {

    private String mode;
    private String title;
    private String displayText;
    private String servicePointName;
    private Integer distanceMeters;
}
