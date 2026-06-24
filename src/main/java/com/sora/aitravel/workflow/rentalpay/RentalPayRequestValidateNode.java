package com.sora.aitravel.workflow.rentalpay;

import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.workflow.WorkflowNode;
import org.springframework.stereotype.Component;

@Component
public class RentalPayRequestValidateNode implements WorkflowNode<RentalPayWorkflowContext> {
    @Override
    public void execute(RentalPayWorkflowContext context) {
        if (context.getUserId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (context.getOrderId() == null || context.getOrderId() <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "订单 ID 不合法");
        }
    }
}
