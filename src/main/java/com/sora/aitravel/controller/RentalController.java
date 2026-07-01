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
import com.sora.aitravel.workflow.rentalcontext.RentalContextPreviewWorkflow;
import com.sora.aitravel.workflow.rentalcontext.RentalContextPreviewWorkflowContext;
import com.sora.aitravel.workflow.rentalorder.RentalOrderCreateWorkflow;
import com.sora.aitravel.workflow.rentalorder.RentalOrderCreateWorkflowContext;
import com.sora.aitravel.workflow.rentalpay.RentalPayWorkflow;
import com.sora.aitravel.workflow.rentalpay.RentalPayWorkflowContext;
import com.sora.aitravel.workflow.rentalquote.RentalQuotePreviewWorkflow;
import com.sora.aitravel.workflow.rentalquote.RentalQuotePreviewWorkflowContext;
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
@RequestMapping("/api/rental")
public class RentalController {
    private final RentalOrderService rentalOrderService;
    private final RentalQuoteService rentalQuoteService;
    private final AlipayPaymentService alipayPaymentService;
    private final RentalContextPreviewWorkflow rentalContextPreviewWorkflow;
    private final RentalQuotePreviewWorkflow rentalQuotePreviewWorkflow;
    private final RentalOrderCreateWorkflow rentalOrderCreateWorkflow;
    private final RentalPayWorkflow rentalPayWorkflow;

    @PostMapping("/context/preview")
    public R<RentalContextPreviewResponse> previewContext(
            @Valid @RequestBody RentalContextPreviewRequest request) {
        RentalContextPreviewWorkflowContext context = new RentalContextPreviewWorkflowContext();
        context.setUserId(LoginUserUtils.getUserId());
        context.setRequest(request);
        return R.ok(rentalContextPreviewWorkflow.execute(context).getResult());
    }

    @PostMapping("/quotes/preview")
    public R<RentalQuotePreviewResponse> preview(
            @Valid @RequestBody RentalQuotePreviewRequest request) {
        RentalQuotePreviewWorkflowContext context = new RentalQuotePreviewWorkflowContext();
        context.setUserId(LoginUserUtils.getUserId());
        context.setRequirement(request.getRequirement());
        return R.ok(rentalQuotePreviewWorkflow.execute(context).getResult());
    }

    @GetMapping("/quotes/latest-ordered")
    public R<List<RentalQuoteOptionDTO>> latestOrderedQuotes() {
        return R.ok(rentalQuoteService.latestOrderedOptions(4));
    }

    @PostMapping("/orders")
    public R<IdResponse> createOrder(@Valid @RequestBody RentalOrderCreateRequest request) {
        RentalOrderCreateWorkflowContext context = new RentalOrderCreateWorkflowContext();
        context.setUserId(LoginUserUtils.getUserId());
        context.setRequest(request);
        return R.ok(new IdResponse(rentalOrderCreateWorkflow.execute(context).getOrderId()));
    }

    @PostMapping("/orders/{id}/pay")
    public R<Void> pay(
            @PathVariable Long id, @RequestBody(required = false) RentalOrderPayRequest request) {
        RentalPayWorkflowContext context = new RentalPayWorkflowContext();
        context.setUserId(LoginUserUtils.getUserId());
        context.setOrderId(id);
        context.setRequest(request);
        rentalPayWorkflow.execute(context);
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
