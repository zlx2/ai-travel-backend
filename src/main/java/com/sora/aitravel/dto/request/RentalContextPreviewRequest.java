package com.sora.aitravel.dto.request;

import com.sora.aitravel.dto.model.TravelRequirementDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RentalContextPreviewRequest {

    @NotNull @Valid private TravelRequirementDTO requirement;

    /** 用户下车、下飞机、酒店或指定接车地址；为空时由目的地推断默认交通枢纽。 */
    private String arrivalText;
}
