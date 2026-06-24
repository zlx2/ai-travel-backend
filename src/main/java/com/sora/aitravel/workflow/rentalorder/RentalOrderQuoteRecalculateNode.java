package com.sora.aitravel.workflow.rentalorder;

import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.dto.model.RentalQuoteOptionDTO;
import com.sora.aitravel.service.RentalQuoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RentalOrderQuoteRecalculateNode {
    private final RentalQuoteService rentalQuoteService;

    public void execute(RentalOrderCreateWorkflowContext context) {
        RentalQuoteOptionDTO selectedQuote = context.getRequest().getSelectedQuote();
        RentalQuoteOptionDTO recalculatedQuote =
                rentalQuoteService.recalculate(
                        context.getRequest().getRequirement(), selectedQuote);
        if (!sameQuote(selectedQuote, recalculatedQuote)) {
            throw new BusinessException(ErrorCode.CONFLICT, "所选车型报价已变化，请重新选择报价");
        }
        context.setRecalculatedQuote(recalculatedQuote);
    }

    private boolean sameQuote(
            RentalQuoteOptionDTO selectedQuote, RentalQuoteOptionDTO recalculatedQuote) {
        return equalsValue(selectedQuote.getVehicleGroupId(), recalculatedQuote.getVehicleGroupId())
                && equalsValue(
                        selectedQuote.getPriceTemplateId(), recalculatedQuote.getPriceTemplateId())
                && equalsValue(selectedQuote.getPickupPoiId(), recalculatedQuote.getPickupPoiId())
                && equalsValue(selectedQuote.getReturnPoiId(), recalculatedQuote.getReturnPoiId())
                && equalsValue(selectedQuote.getRentalDays(), recalculatedQuote.getRentalDays())
                && equalsValue(totalPrice(selectedQuote), totalPrice(recalculatedQuote));
    }

    private Integer totalPrice(RentalQuoteOptionDTO quote) {
        return quote.getFeeBreakdown() == null ? null : quote.getFeeBreakdown().getTotalPriceCent();
    }

    private boolean equalsValue(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }
}
