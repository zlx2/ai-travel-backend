package com.sora.aitravel.controller;

import com.sora.aitravel.service.AlipayPaymentService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/payment/alipay")
public class AlipayNotifyController {

    private final AlipayPaymentService alipayPaymentService;

    @PostMapping(value = "/notify", produces = MediaType.TEXT_PLAIN_VALUE)
    public String notify(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap()
                .forEach(
                        (key, values) ->
                                params.put(
                                        key,
                                        values == null || values.length == 0 ? "" : values[0]));
        return alipayPaymentService.handleNotify(params) ? "success" : "failure";
    }

    @GetMapping(value = "/return", produces = MediaType.TEXT_PLAIN_VALUE)
    public String returnPage(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap()
                .forEach(
                        (key, values) ->
                                params.put(
                                        key,
                                        values == null || values.length == 0 ? "" : values[0]));
        log.info(
                "支付宝同步返回，outTradeNo={}, tradeNo={}, totalAmount={}",
                params.get("out_trade_no"),
                params.get("trade_no"),
                params.get("total_amount"));
        return "payment_return_received";
    }
}
