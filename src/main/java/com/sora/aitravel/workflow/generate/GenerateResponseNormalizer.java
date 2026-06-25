package com.sora.aitravel.workflow.generate;

import org.springframework.stereotype.Component;

/** Graph 执行后的响应规整入口；新生成链路直接产出 DTO，这里只保留扩展点。 */
@Component
public class GenerateResponseNormalizer {

    public void normalize(GenerateWorkflowContext context) {
        // Spring AI Alibaba Graph 当前保留对象引用，暂不需要 Map -> DTO 重建。
    }
}
