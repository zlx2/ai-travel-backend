package com.sora.aitravel.workflow.rentalorder;

import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.dto.model.RentalQuoteOptionDTO;
import com.sora.aitravel.service.RentalQuoteService;
import org.springframework.stereotype.Component;

@Component
public class RentalOrderQuoteRecalculateNode {
    private final RentalQuoteService rentalQuoteService;

    public RentalOrderQuoteRecalculateNode(RentalQuoteService rentalQuoteService) {
        this.rentalQuoteService = rentalQuoteService;
    }

    public void execute(RentalOrderCreateWorkflowContext context) {
        RentalQuoteOptionDTO selectedQuote = context.getRequest().selectedQuote();
        RentalQuoteOptionDTO recalculatedQuote =
                rentalQuoteService.recalculate(context.getRequest().requirement(), selectedQuote);
        if (!selectedQuote.vehicleGroupId().equals(recalculatedQuote.vehicleGroupId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "所选车型报价已变化，请重新选择报价");
        }
        context.setRecalculatedQuote(recalculatedQuote);
    }
}
