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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SaCheckLogin
@RestController
@RequiredArgsConstructor
@RequestMapping("/rental")
public class RentalController {
    private final RentalOrderService rentalOrderService;
    private final RentalQuoteService rentalQuoteService;
    private final AlipayPaymentService alipayPaymentService;

    @PostMapping("/context/preview")
    public R<RentalContextPreviewResponse> previewContext(
            @Valid @RequestBody RentalContextPreviewRequest request) {
        return R.ok(rentalQuoteService.previewContext(request));
    }

    @PostMapping("/quotes/preview")
    public R<RentalQuotePreviewResponse> preview(
            @Valid @RequestBody RentalQuotePreviewRequest request) {
        return R.ok(rentalQuoteService.preview(request.getRequirement()));
    }

    @GetMapping("/quotes/latest-ordered")
    public R<List<RentalQuoteOptionDTO>> latestOrderedQuotes() {
        return R.ok(rentalQuoteService.latestOrderedOptions(4));
    }

    @PostMapping("/orders")
    public R<IdResponse> createOrder(@Valid @RequestBody RentalOrderCreateRequest request) {
        return R.ok(new IdResponse(rentalOrderService.create(LoginUserUtils.getUserId(), request)));
    }

    @PostMapping("/orders/{id}/pay")
    public R<Void> pay(
            @PathVariable Long id, @RequestBody(required = false) RentalOrderPayRequest request) {
        rentalOrderService.pay(LoginUserUtils.getUserId(), id, request);
        return R.ok();
    }

    @PostMapping("/orders/{id}/alipay/page-pay")
    public R<AlipayPagePayResponse> alipayPagePay(@PathVariable Long id) {
        return R.ok(alipayPaymentService.createRentalPagePay(LoginUserUtils.getUserId(), id));
    }

    @GetMapping("/orders/my")
    public R<List<RentalOrderResponse>> listMy() {
        return R.ok(rentalOrderService.listMy(LoginUserUtils.getUserId()));
    }

    @GetMapping("/orders/{id}")
    public R<RentalOrderResponse> detail(@PathVariable Long id) {
        return R.ok(rentalOrderService.get(LoginUserUtils.getUserId(), id));
    }

    @PostMapping("/orders/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) {
        rentalOrderService.cancel(LoginUserUtils.getUserId(), id);
        return R.ok();
    }
}
