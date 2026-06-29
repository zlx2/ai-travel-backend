package com.sora.aitravel.controller;

import com.sora.aitravel.service.AlipayPaymentService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payment/alipay")
public class AlipayNotifyController {

    private final AlipayPaymentService alipayPaymentService;

    @PostMapping(value = "/notify", produces = MediaType.TEXT_PLAIN_VALUE)
    public String notify(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap()
                .forEach((key, values) -> params.put(key, values == null || values.length == 0 ? "" : values[0]));
        return alipayPaymentService.handleNotify(params) ? "success" : "failure";
    }
}
