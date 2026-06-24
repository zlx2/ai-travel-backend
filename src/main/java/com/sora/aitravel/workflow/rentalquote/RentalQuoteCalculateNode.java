package com.sora.aitravel.workflow.rentalquote;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RentalQuoteCalculateNode {
    private final MockRentalQuoteFactory mockRentalQuoteFactory;

    public RentalQuoteCalculateNode(MockRentalQuoteFactory mockRentalQuoteFactory) {
        this.mockRentalQuoteFactory = mockRentalQuoteFactory;
    }

    public void execute(RentalQuotePreviewWorkflowContext context) {
        // TODO 租车业务接入后恢复调用 RentalQuoteService.preview。
        log.info("节点[rental-quote-calculate]：租车业务未接入，返回模拟报价用于前后端联调。");
        context.setResult(mockRentalQuoteFactory.preview(context.getRequirement()));
    }
}
