package com.sora.aitravel.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentalArrivalPointDTO {

    private String name;
    private String cityName;
    private String source;
}
