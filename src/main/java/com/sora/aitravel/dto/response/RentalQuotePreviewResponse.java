package com.sora.aitravel.dto.response;

import com.sora.aitravel.dto.model.RentalQuoteOptionDTO;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RentalQuotePreviewResponse {

    private String routeMode;
    private String rentalCity;
    private String citycode;
    private List<RentalQuoteOptionDTO> quoteOptions;
}
