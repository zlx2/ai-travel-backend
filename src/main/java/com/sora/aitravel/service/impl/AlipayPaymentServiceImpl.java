package com.sora.aitravel.service.impl;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.config.AlipayProperties;
import com.sora.aitravel.dto.response.AlipayPagePayResponse;
import com.sora.aitravel.entity.RentalOrder;
import com.sora.aitravel.mapper.RentalOrderMapper;
import com.sora.aitravel.service.AlipayPaymentService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlipayPaymentServiceImpl implements AlipayPaymentService {

    private static final String PRODUCT_CODE = "FAST_INSTANT_TRADE_PAY";

    private final AlipayProperties properties;
    private final RentalOrderMapper rentalOrderMapper;
    private final ObjectMapper objectMapper;

    @Override
    public AlipayPagePayResponse createRentalPagePay(Long userId, Long orderId) {
        ensureConfigured();
        RentalOrder order = rentalOrderMapper.selectById(orderId);
        if (order == null || !userId.equals(order.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "租车订单不存在");
        }
        if (!"pending".equals(order.getOrderStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "只有待支付订单可以发起支付");
        }

        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setNotifyUrl(configValue(properties.getNotifyUrl()));
        request.setReturnUrl(configValue(properties.getReturnUrl()));
        request.setBizContent(buildBizContent(order));
        try {
            String formHtml = alipayClient().pageExecute(request, "POST").getBody();
            return AlipayPagePayResponse.builder()
                    .orderId(order.getId())
                    .orderNo(order.getOrderNo())
                    .formHtml(formHtml)
                    .build();
        } catch (AlipayApiException exception) {
            log.error("支付宝沙箱支付表单生成失败，orderId={}", orderId, exception);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "支付宝支付表单生成失败");
        }
    }

    @Override
    @Transactional
    public boolean handleNotify(Map<String, String> params) {
        ensureConfigured();
        if (!verify(params)) {
            log.warn("支付宝异步通知验签失败，params={}", params);
            return false;
        }
        if (!properties.getAppId().equals(params.get("app_id"))) {
            log.warn("支付宝异步通知 app_id 不匹配，app_id={}", params.get("app_id"));
            return false;
        }
        String tradeStatus = params.get("trade_status");
        if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
            log.info("支付宝异步通知非成功状态，tradeStatus={}", tradeStatus);
            return true;
        }
        String orderNo = params.get("out_trade_no");
        RentalOrder order =
                rentalOrderMapper.selectOne(
                        new LambdaQueryWrapper<RentalOrder>().eq(RentalOrder::getOrderNo, orderNo));
        if (order == null) {
            log.warn("支付宝异步通知订单不存在，orderNo={}", orderNo);
            return false;
        }
        if (!amountMatches(order, params.get("total_amount"))) {
            log.warn(
                    "支付宝异步通知金额不匹配，orderNo={}, totalAmount={}", orderNo, params.get("total_amount"));
            return false;
        }
        if ("paid".equals(order.getPaymentStatus())) {
            return true;
        }
        if (!"pending".equals(order.getOrderStatus())) {
            log.warn("支付宝异步通知订单状态不可支付，orderNo={}, orderStatus={}", orderNo, order.getOrderStatus());
            return false;
        }
        order.setPaymentStatus("paid");
        order.setOrderStatus("confirmed");
        order.setUpdateTime(LocalDateTime.now());
        rentalOrderMapper.updateById(order);
        return true;
    }

    private AlipayClient alipayClient() {
        return new DefaultAlipayClient(
                configValue(properties.getGatewayUrl()),
                configValue(properties.getAppId()),
                keyValue(properties.getAppPrivateKey()),
                "json",
                configValue(properties.getCharset()),
                keyValue(properties.getAlipayPublicKey()),
                configValue(properties.getSignType()));
    }

    private boolean verify(Map<String, String> params) {
        try {
            return AlipaySignature.rsaCheckV1(
                    params,
                    keyValue(properties.getAlipayPublicKey()),
                    configValue(properties.getCharset()),
                    configValue(properties.getSignType()));
        } catch (AlipayApiException exception) {
            log.warn("支付宝异步通知验签异常", exception);
            return false;
        }
    }

    private String buildBizContent(RentalOrder order) {
        Map<String, String> bizContent = new LinkedHashMap<>();
        bizContent.put("out_trade_no", order.getOrderNo());
        bizContent.put("total_amount", yuan(order.getTotalPriceCent()));
        bizContent.put("subject", "PlanGo租车订单-" + order.getOrderNo());
        bizContent.put("product_code", PRODUCT_CODE);
        try {
            return objectMapper.writeValueAsString(bizContent);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "支付宝支付参数生成失败");
        }
    }

    private boolean amountMatches(RentalOrder order, String totalAmount) {
        try {
            return yuan(order.getTotalPriceCent())
                    .equals(
                            new BigDecimal(totalAmount)
                                    .setScale(2, RoundingMode.HALF_UP)
                                    .toPlainString());
        } catch (Exception e) {
            return false;
        }
    }

    private String yuan(Integer cent) {
        return BigDecimal.valueOf(cent == null ? 0 : cent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    private void ensureConfigured() {
        if (!Boolean.TRUE.equals(properties.getEnabled())) {
            throw new BusinessException(
                    ErrorCode.PARAM_ERROR, "支付宝沙箱支付未启用：请设置 ALIPAY_ENABLED=true");
        }
        if (isBlank(configValue(properties.getAppId()))
                || isBlank(keyValue(properties.getAppPrivateKey()))
                || isBlank(keyValue(properties.getAlipayPublicKey()))
                || isBlank(configValue(properties.getNotifyUrl()))
                || isBlank(configValue(properties.getReturnUrl()))) {
            throw new BusinessException(
                    ErrorCode.PARAM_ERROR, "支付宝沙箱配置不完整：需要 APP_ID、应用私钥、支付宝公钥、notifyUrl 和 returnUrl");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String configValue(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() >= 2
                && ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                        || (trimmed.startsWith("'") && trimmed.endsWith("'")))) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private String keyValue(String value) {
        String normalized = configValue(value);
        if (normalized == null) {
            return null;
        }
        return normalized
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
    }
}
