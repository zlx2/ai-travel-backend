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
        if (!sameQuote(selectedQuote, recalculatedQuote)) {
            throw new BusinessException(ErrorCode.CONFLICT, "所选车型报价已变化，请重新选择报价");
        }
        context.setRecalculatedQuote(recalculatedQuote);
    }

    private boolean sameQuote(
            RentalQuoteOptionDTO selectedQuote, RentalQuoteOptionDTO recalculatedQuote) {
        return equalsValue(selectedQuote.vehicleGroupId(), recalculatedQuote.vehicleGroupId())
                && equalsValue(selectedQuote.priceTemplateId(), recalculatedQuote.priceTemplateId())
                && equalsValue(selectedQuote.pickupPoiId(), recalculatedQuote.pickupPoiId())
                && equalsValue(selectedQuote.returnPoiId(), recalculatedQuote.returnPoiId())
                && equalsValue(selectedQuote.rentalDays(), recalculatedQuote.rentalDays())
                && equalsValue(totalPrice(selectedQuote), totalPrice(recalculatedQuote));
    }

    private Integer totalPrice(RentalQuoteOptionDTO quote) {
        return quote.feeBreakdown() == null ? null : quote.feeBreakdown().totalPriceCent();
    }

    private boolean equalsValue(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }
}
