package com.sora.aitravel.workflow.analyze;

import com.sora.aitravel.workflow.WorkflowNode;
import org.springframework.stereotype.Component;

/**
 * 结果合并节点。
 * <p>
 * 实现 {@link WorkflowNode} 接口，是 {@link TripAnalyzeWorkflow} 工作流的第六个步骤。
 * 负责将前面各节点（信息提取、完整性检查、目的地推荐、冲突检测）产生的中间结果
 * 合并为一个统一的、结构化的 {@link com.sora.aitravel.dto.response.TripAnalyzeResponse}。
 * 同时也负责将本次分析与用户之前的会话历史进行合并。
 * <p>
 * 在整个工作流中的位置：流程第 6 步（冲突检测之后，JSON 校验之前）。
 * <p>
 * 输入：{@link AnalyzeWorkflowContext} 中各节点写入的中间数据。
 * 输出：将合并后的最终结果写入 {@link AnalyzeWorkflowContext#result}。
 */
@Component
public class AnalyzeResultMergeNode implements WorkflowNode<AnalyzeWorkflowContext> {

    /**
     * 执行结果合并逻辑——将各节点产出整合为最终响应结构。
     *
     * @param context 工作流上下文，读取各中间数据并合并为最终结果
     */
    public void execute(AnalyzeWorkflowContext context) {
        /* TODO merge prior conversation */
    }
}
