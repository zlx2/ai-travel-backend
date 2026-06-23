package com.sora.aitravel.workflow.rental;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 租车工作流。
 *
 * <p>当前版本只完成“目标地点 -> 推荐租车服务点”的最小闭环。后续完整租车流程应在这里继续编排库存查询、
 * 价格计算、异店还车校验和结果合并节点。
 */
@Component
@RequiredArgsConstructor
public class RentalWorkflow {

    /** 租车服务点解析节点。 */
    private final RentalStoreResolveNode rentalStoreResolveNode;

    /**
     * 执行租车工作流。
     *
     * @param context 租车工作流上下文
     * @return 写入中间和最终结果后的上下文
     */
    public RentalWorkflowContext execute(RentalWorkflowContext context) {
        rentalStoreResolveNode.execute(context);
        return context;
    }
}
