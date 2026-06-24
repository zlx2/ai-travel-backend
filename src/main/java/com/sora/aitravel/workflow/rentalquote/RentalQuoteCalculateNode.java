package com.sora.aitravel.workflow.rentalquote;

import com.sora.aitravel.service.RentalQuoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RentalQuoteCalculateNode {
    private final RentalQuoteService rentalQuoteService;

    public void execute(RentalQuotePreviewWorkflowContext context) {
        log.info("节点[rental-quote-calculate]：根据租车价格模板、车型组和取还车点计算报价。");
        context.setResult(rentalQuoteService.preview(context.getRequirement()));
    }
}
