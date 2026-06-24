package com.sora.aitravel.dto.response;

import com.sora.aitravel.dto.model.RentalFeeBreakdownDTO;
import java.time.LocalDateTime;
import java.util.Map;

public record RentalOrderResponse(
        Long id,
        String orderNo,
        Long tripId,
        String rentalCity,
        Long vehicleGroupId,
        String orderStatus,
        String paymentStatus,
        Integer totalPriceCent,
        RentalFeeBreakdownDTO feeBreakdown,
        Map<String, Object> pickupPoiSnapshot,
        Map<String, Object> returnPoiSnapshot,
        Map<String, Object> priceSnapshot,
        LocalDateTime pickupTime,
        LocalDateTime returnTime,
        LocalDateTime createTime) {}
