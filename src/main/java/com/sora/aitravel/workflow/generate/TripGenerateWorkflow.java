package com.sora.aitravel.workflow.generate;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 结构化行程生成 Workflow。
 *
 * <p>调用模型前必须校验 departure、destination、days；模型输出非法时最多修复一次，修复失败直接返回 AI 错误码。
 */
@Component
@RequiredArgsConstructor
public class TripGenerateWorkflow {
    private final RequirementValidateNode requirementValidateNode;
    private final TripPlanGenerateNode tripPlanGenerateNode;
    private final GenerateJsonValidateNode generateJsonValidateNode;
    private final GenerateJsonRepairNode generateJsonRepairNode;
    private final GenerateResultMergeNode generateResultMergeNode;

    public GenerateWorkflowContext execute(GenerateWorkflowContext context) {
        // Generate 不负责自动保存行程，持久化必须由用户随后调用 Trip 接口触发。
        requirementValidateNode.execute(context);
        tripPlanGenerateNode.execute(context);
        generateJsonValidateNode.execute(context);
        generateJsonRepairNode.execute(context);
        generateResultMergeNode.execute(context);
        return context;
    }
}
