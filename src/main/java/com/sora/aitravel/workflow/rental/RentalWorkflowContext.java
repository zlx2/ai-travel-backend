package com.sora.aitravel.workflow.rental;

import com.sora.aitravel.dto.model.RentalStoreDTO;
import com.sora.aitravel.dto.model.RentalStoreResolveCommand;
import lombok.Data;

/**
 * 租车工作流上下文。
 *
 * <p>该对象是租车工作流的数据总线：Controller 或上游节点写入输入命令，各 Node 依次读取并写入中间产物。 当前只承载租车服务点解析结果，后续可继续扩展库存、价格、订单等字段。
 */
@Data
public class RentalWorkflowContext {
    /** 当前操作用户 ID；公开或调试流程可为空。 */
    private Long userId;

    /** 待解析的取车或还车服务点命令。 */
    private RentalStoreResolveCommand storeResolveCommand;

    /** 已解析出的推荐租车服务点，供库存、价格等后续节点使用。 */
    private RentalStoreDTO resolvedStore;
}
