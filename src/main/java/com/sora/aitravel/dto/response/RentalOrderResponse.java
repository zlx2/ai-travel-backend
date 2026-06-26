package com.sora.aitravel.dto.response;

import com.sora.aitravel.dto.model.RentalFeeBreakdownDTO;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentalOrderResponse {

    private Long id;
    private String orderNo;
    private Long tripId;
    private String rentalCity;
    private Long vehicleGroupId;
    private String orderStatus;
    private String paymentStatus;
    private Integer totalPriceCent;
    private RentalFeeBreakdownDTO feeBreakdown;
    private Map<String, Object> pickupPoiSnapshot;
    private Map<String, Object> returnPoiSnapshot;
    private Map<String, Object> priceSnapshot;
    private LocalDateTime pickupTime;
    private LocalDateTime returnTime;
    private LocalDateTime createTime;
}
