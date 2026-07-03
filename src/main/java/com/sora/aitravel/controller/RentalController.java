package com.sora.aitravel.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.sora.aitravel.common.result.IdResponse;
import com.sora.aitravel.common.result.R;
import com.sora.aitravel.common.utils.LoginUserUtils;
import com.sora.aitravel.dto.model.RentalQuoteOptionDTO;
import com.sora.aitravel.dto.request.RentalContextPreviewRequest;
import com.sora.aitravel.dto.request.RentalOrderCreateRequest;
import com.sora.aitravel.dto.request.RentalOrderPayRequest;
import com.sora.aitravel.dto.request.RentalQuotePreviewRequest;
import com.sora.aitravel.dto.response.AlipayPagePayResponse;
import com.sora.aitravel.dto.response.RentalContextPreviewResponse;
import com.sora.aitravel.dto.response.RentalOrderResponse;
import com.sora.aitravel.dto.response.RentalQuotePreviewResponse;
import com.sora.aitravel.service.AlipayPaymentService;
import com.sora.aitravel.service.RentalOrderService;
import com.sora.aitravel.service.RentalQuoteService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 租车服务控制器
 * 提供租车报价预览、订单创建、支付等相关接口
 */
@SaCheckLogin
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/rental")
public class RentalController {
    private final RentalOrderService rentalOrderService;
    private final RentalQuoteService rentalQuoteService;
    private final AlipayPaymentService alipayPaymentService;

    /**
     * 预览租车上下文信息
     *
     * @param request 上下文预览请求，包含目的地、到达信息等
     * @return 上下文预览响应
     */
    @PostMapping("/context/preview")
    public R<RentalContextPreviewResponse> previewContext(
            @Valid @RequestBody RentalContextPreviewRequest request) {
        log.info(
                "租车上下文预览请求进入，userId={}, destination={}, arrivalText={}",
                LoginUserUtils.getUserId(),
                request.getRequirement() == null ? null : request.getRequirement().getDestination(),
                request.getArrivalText());
        return R.ok(rentalQuoteService.previewContext(request));
    }

    /**
     * 预览租车报价
     *
     * @param request 报价预览请求，包含需求信息
     * @return 报价预览响应
     */
    @PostMapping("/quotes/preview")
    public R<RentalQuotePreviewResponse> preview(
            @Valid @RequestBody RentalQuotePreviewRequest request) {
        log.info(
                "租车报价预览请求进入，userId={}, destination={}, routeMode={}",
                LoginUserUtils.getUserId(),
                request.getRequirement() == null ? null : request.getRequirement().getDestination(),
                request.getRequirement() == null ? null : request.getRequirement().getRouteMode());
        return R.ok(rentalQuoteService.preview(request.getRequirement()));
    }

    /**
     * 获取最近已订购的租车报价选项
     *
     * @return 最近4个租车报价选项列表
     */
    @GetMapping("/quotes/latest-ordered")
    public R<List<RentalQuoteOptionDTO>> latestOrderedQuotes() {
        log.info("最近租车报价请求进入，userId={}", LoginUserUtils.getUserId());
        return R.ok(rentalQuoteService.latestOrderedOptions(4));
    }

    /**
     * 创建租车订单
     *
     * @param request 订单创建请求，包含会话ID和选中的报价信息
     * @return 订单ID响应
     */
    @PostMapping("/orders")
    public R<IdResponse> createOrder(@Valid @RequestBody RentalOrderCreateRequest request) {
        log.info(
                "租车订单创建请求进入，userId={}, conversationId={}, selectedQuote={}",
                LoginUserUtils.getUserId(),
                request.getConversationId(),
                request.getSelectedQuote() == null
                        ? null
                        : request.getSelectedQuote().getQuoteId());
        return R.ok(new IdResponse(rentalOrderService.create(LoginUserUtils.getUserId(), request)));
    }

    /**
     * 模拟支付租车订单
     *
     * @param id      订单ID
     * @param request 支付请求（可选）
     * @return 空响应
     */
    @PostMapping("/orders/{id}/pay")
    public R<Void> pay(
            @PathVariable Long id, @RequestBody(required = false) RentalOrderPayRequest request) {
        log.info("租车订单模拟支付请求进入，userId={}, orderId={}", LoginUserUtils.getUserId(), id);
        rentalOrderService.pay(LoginUserUtils.getUserId(), id, request);
        return R.ok();
    }

    /**
     * 支付宝页面支付
     *
     * @param id 订单ID
     * @return 支付宝页面支付响应
     */
    @PostMapping("/orders/{id}/alipay/page-pay")
    public R<AlipayPagePayResponse> alipayPagePay(@PathVariable Long id) {
        log.info("租车订单支付宝支付请求进入，userId={}, orderId={}", LoginUserUtils.getUserId(), id);
        return R.ok(alipayPaymentService.createRentalPagePay(LoginUserUtils.getUserId(), id));
    }

    /**
     * 获取我的租车订单列表
     *
     * @return 租车订单列表
     */
    @GetMapping("/orders/my")
    public R<List<RentalOrderResponse>> listMy() {
        return R.ok(rentalOrderService.listMy(LoginUserUtils.getUserId()));
    }

    /**
     * 获取租车订单详情
     *
     * @param id 订单ID
     * @return 租车订单详情
     */
    @GetMapping("/orders/{id}")
    public R<RentalOrderResponse> detail(@PathVariable Long id) {
        return R.ok(rentalOrderService.get(LoginUserUtils.getUserId(), id));
    }

    /**
     * 取消租车订单
     *
     * @param id 订单ID
     * @return 空响应
     */
    @PostMapping("/orders/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) {
        rentalOrderService.cancel(LoginUserUtils.getUserId(), id);
        return R.ok();
    }
}