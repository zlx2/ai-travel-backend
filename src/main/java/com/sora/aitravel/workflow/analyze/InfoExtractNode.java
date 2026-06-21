package com.sora.aitravel.workflow.analyze;

import com.sora.aitravel.workflow.WorkflowNode;
import org.springframework.stereotype.Component;

/**
 * 信息提取节点。
 * <p>
 * 实现 {@link WorkflowNode} 接口，是 {@link TripAnalyzeWorkflow} 工作流的第二个步骤。
 * 负责调用 AI 大模型（如 DeepSeek）从用户标准化的自然语言输入中提取关键行程字段，
 * 包括出发地、目的地、出行天数、出行时间、偏好等结构化信息。
 * <p>
 * 在整个工作流中的位置：流程第 2 步（在输入预处理之后，完整性检查之前）。
 * <p>
 * 输入：{@link AnalyzeWorkflowContext#request}（已预处理的用户请求）。
 * 输出：将模型返回的原始 JSON 响应写入 {@link AnalyzeWorkflowContext#rawModelResponse}。
 */
@Component
public class InfoExtractNode implements WorkflowNode<AnalyzeWorkflowContext> {

    /**
     * 执行信息提取逻辑——调用 AI 模型从用户输入中提取结构化行程信息。
     *
     * @param context 工作流上下文，读取预处理后的请求并调用模型，
     *                将模型原始响设置到 {@link AnalyzeWorkflowContext#rawModelResponse}
     */
    public void execute(AnalyzeWorkflowContext context) {
        /* TODO call DeepSeek */
    }
}
