package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.workflow.WorkflowNode;
import org.springframework.stereotype.Component;

/**
 * 生成结果合并节点。
 * <p>
 * 实现 {@link WorkflowNode} 接口，是 {@link TripGenerateWorkflow} 工作流的第五个（最后）步骤。
 * 负责将从 AI 模型获取的原始 JSON 行程数据（经校验/修复后）转换为标准化的
 * {@link com.sora.aitravel.dto.response.TripGenerateResponse} 结构，
 * 包括构建每日活动列表、景点详情等，供 Controller 层返回给前端。
 * <p>
 * 在整个工作流中的位置：生成流程第 5 步（最后执行）。
 * <p>
 * 输入：{@link GenerateWorkflowContext#rawModelResponse}（校验/修复后的 JSON）。
 * 输出：格式化的最终响应写入 {@link GenerateWorkflowContext#result}。
 */
@Component
public class GenerateResultMergeNode implements WorkflowNode<GenerateWorkflowContext> {

    /**
     * 执行结果合并逻辑——将模型输出转换为标准响应结构。
     *
     * @param context 工作流上下文，读取模型响应 JSON 并构建最终结果
     */
    public void execute(GenerateWorkflowContext context) {
        /* TODO build response */
    }
}
