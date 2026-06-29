package com.sora.aitravel.service;

import com.sora.aitravel.dto.response.AlipayPagePayResponse;
import java.util.Map;

public interface AlipayPaymentService {

    AlipayPagePayResponse createRentalPagePay(Long userId, Long orderId);

    boolean handleNotify(Map<String, String> params);
}
