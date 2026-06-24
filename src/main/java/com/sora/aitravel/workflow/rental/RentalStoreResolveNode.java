package com.sora.aitravel.workflow.rental;

import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.dto.model.RentalStoreResolveCommand;
import com.sora.aitravel.service.RentalStoreService;
import org.springframework.stereotype.Component;

/**
 * 租车服务点解析节点。
 *
 * <p>该节点是工作流中的中间步骤：读取上下文中的目标地点、城市和取还车用途，调用 {@link RentalStoreService} 解析一个推荐服务点，并写回上下文。它不直接调用高德
 * API，也不负责库存、价格或订单。
 */
@Component
public class RentalStoreResolveNode {

    private final RentalStoreService rentalStoreService;

    public RentalStoreResolveNode(RentalStoreService rentalStoreService) {
        this.rentalStoreService = rentalStoreService;
    }

    public void execute(RentalWorkflowContext context) {
        RentalStoreResolveCommand command = context.getStoreResolveCommand();
        if (command == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "租车服务点解析命令不能为空");
        }

        context.setResolvedStore(rentalStoreService.resolveRentalStore(command));
    }
}
