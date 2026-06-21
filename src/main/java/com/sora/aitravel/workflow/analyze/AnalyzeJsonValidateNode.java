package com.sora.aitravel.workflow.analyze;

import com.sora.aitravel.workflow.WorkflowNode;
import org.springframework.stereotype.Component;

/**
 * JSON 校验节点。
 * <p>
 * 实现 {@link WorkflowNode} 接口，是 {@link TripAnalyzeWorkflow} 工作流的第七个步骤。
 * 负责验证 AI 模型返回的 JSON 响应是否符合预定义的合法状态结构（四种合法状态），
 * 确保 JSON 格式正确、字段完整、值在合法范围内。
 * <p>
 * 在整个工作流中的位置：流程第 7 步（结果合并之后，修复节点之前）。
 * 若校验通过则跳过修复节点（由修复节点内部判断）。
 * <p>
 * 输入：{@link AnalyzeWorkflowContext#rawModelResponse}（模型返回的 JSON 字符串）。
 * 输出：将校验结果（通过/失败及错误详情）写入上下文，供修复节点决策。
 */
@Component
public class AnalyzeJsonValidateNode implements WorkflowNode<AnalyzeWorkflowContext> {

    /**
     * 执行 JSON 校验逻辑——验证模型响应的 JSON 结构和合法性。
     *
     * @param context 工作流上下文，读取模型响应 JSON 并执行校验
     */
    public void execute(AnalyzeWorkflowContext context) {
        /* TODO validate four legal statuses */
    }
}
