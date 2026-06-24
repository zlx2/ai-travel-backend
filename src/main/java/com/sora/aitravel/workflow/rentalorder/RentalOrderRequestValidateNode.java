package com.sora.aitravel.workflow.rentalorder;

import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.dto.request.RentalOrderCreateRequest;
import org.springframework.stereotype.Component;

@Component
public class RentalOrderRequestValidateNode {
    public void execute(RentalOrderCreateWorkflowContext context) {
        if (context.getUserId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        RentalOrderCreateRequest request = context.getRequest();
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "租车订单请求不能为空");
        }
        if (request.getRequirement() == null
                || request.getTripPlan() == null
                || request.getSelectedQuote() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "下单必须包含行程需求、行程方案和已选报价");
        }
        if (request.getRequirement().getDays() == null || request.getRequirement().getDays() < 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "行程天数不合法");
        }
    }
}
