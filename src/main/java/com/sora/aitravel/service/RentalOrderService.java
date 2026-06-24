package com.sora.aitravel.service;

import com.sora.aitravel.dto.request.RentalOrderCreateRequest;
import com.sora.aitravel.dto.request.RentalOrderPayRequest;
import com.sora.aitravel.dto.response.RentalOrderResponse;
import java.util.List;

public interface RentalOrderService {
    Long create(Long userId, RentalOrderCreateRequest request);

    void pay(Long userId, Long id, RentalOrderPayRequest request);

    List<RentalOrderResponse> listMy(Long userId);

    RentalOrderResponse get(Long userId, Long id);

    void cancel(Long userId, Long id);
}
