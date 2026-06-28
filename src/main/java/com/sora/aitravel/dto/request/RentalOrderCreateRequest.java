package com.sora.aitravel.dto.request;

import com.sora.aitravel.dto.model.RentalQuoteOptionDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.model.TripPlanDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RentalOrderCreateRequest {

    private String conversationId;
    @NotNull @Valid private TravelRequirementDTO requirement;
    @NotNull @Valid private TripPlanDTO tripPlan;
    @NotNull private RentalQuoteOptionDTO selectedQuote;
    private String protectionPackageCode;
    private String protectionPackageName;
    private Integer protectionFeeCent;
    private String contactName;
    private String contactPhone;
    private String remark;
}
