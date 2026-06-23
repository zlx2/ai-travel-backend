package com.sora.aitravel.workflow.analyze;

import com.sora.aitravel.workflow.WorkflowNode;
import org.springframework.stereotype.Component;

/**
 * 完整性检查节点。
 * <p>
 * 实现 {@link WorkflowNode} 接口，是 {@link TripAnalyzeWorkflow} 工作流的第三个步骤。
 * 负责检查信息提取后的行程数据是否包含所有必要字段，例如出发地（departure）、
 * 目的地（destination）、出行天数（days）等。如果关键字段缺失，
 * 则设置对应的错误状态或触发补充询问。
 * <p>
 * 在整个工作流中的位置：流程第 3 步（信息提取之后，目的地推荐之前）。
 * <p>
 * 输入：{@link AnalyzeWorkflowContext#rawModelResponse}（模型提取的结构化信息）。
 * 输出：将完整性检查结果写入 {@link AnalyzeWorkflowContext#result} 或上下文中的状态字段。
 */
@Component
public class CompletenessCheckNode implements WorkflowNode<AnalyzeWorkflowContext> {

    /**
     * 执行完整性检查——确认出行必要字段是否齐全。
     *
     * @param context 工作流上下文，读取模型提取的信息并检查完整性
     */
    public void execute(AnalyzeWorkflowContext context) {
        if (context.getExtractedRequirement() == null
                || context.getExtractedRequirement().destination() == null
                || context.getExtractedRequirement().destination().isBlank()) {
            context.setStatus("NEED_DESTINATION_CHOICE");
            return;
        }
        context.setStatus("READY");
    }
}
