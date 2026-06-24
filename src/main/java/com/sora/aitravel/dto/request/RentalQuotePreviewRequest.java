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
public class RentalQuotePreviewRequest {

    @NotNull @Valid private TravelRequirementDTO requirement;
}
