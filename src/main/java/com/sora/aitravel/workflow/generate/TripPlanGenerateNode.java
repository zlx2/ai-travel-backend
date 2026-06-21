package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.workflow.WorkflowNode;
import org.springframework.stereotype.Component;

/**
 * 行程计划生成节点。
 * <p>
 * 实现 {@link WorkflowNode} 接口，是 {@link TripGenerateWorkflow} 工作流的第二个步骤。
 * 负责调用 AI 大模型（如 DeepSeek），根据用户提供的出发地、目的地、出行天数
 * 和其他偏好信息，生成结构化的每日行程计划。模型输出的应为包含每日活动安排的
 * JSON 格式数据。
 * <p>
 * 在整个工作流中的位置：生成流程第 2 步（需求校验之后，JSON 校验之前）。
 * <p>
 * 输入：{@link GenerateWorkflowContext#request}（已校验的生成请求）。
 * 输出：模型返回的原始 JSON 写入 {@link GenerateWorkflowContext#rawModelResponse}。
 */
@Component
public class TripPlanGenerateNode implements WorkflowNode<GenerateWorkflowContext> {

    /**
     * 执行行程计划生成逻辑——调用 AI 模型生成结构化行程 JSON。
     *
     * @param context 工作流上下文，读取生成请求并调用模型，将原始响应存入上下文
     */
    public void execute(GenerateWorkflowContext context) {
        /* TODO call model */
    }
}
