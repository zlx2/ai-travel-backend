package com.sora.aitravel.workflow.chat;

import org.springframework.stereotype.Component;

/**
 * 模型调用节点。
 *
 * <p>实现 Spring AI Alibaba Graph node 接口，是 {@link AiChatWorkflow} 工作流的第四个步骤。 负责调用 AI 大模型（如
 * DeepSeek）执行实际的推理请求。将构建好的 Prompt 发送给模型，并获取原始回复文本。
 *
 * <p>此节点也用于其他工作流（如 {@link com.sora.aitravel.workflow.analyze.InfoExtractNode}、 {@link
 * com.sora.aitravel.workflow.generate.TripPlanGenerateNode}）中， 是与 AI 模型交互的核心节点。
 *
 * <p>在整个工作流中的位置：聊天流程第 4 步（Prompt 构建之后，结果格式化之前）。
 *
 * <p>输入：{@link ChatWorkflowContext#prompt}（构建完成的提示词）。 输出：模型原始响应文本写入 {@link
 * ChatWorkflowContext#rawModelResponse}。
 */
@Component
public class ModelCallNode {

    /**
     * 执行模型调用逻辑——发送 Prompt 给 AI 模型并获取回复。
     *
     * @param context 工作流上下文，读取 Prompt 并调用模型，将响应存入上下文
     */
    public void execute(ChatWorkflowContext context) {
        /* TODO call DeepSeek */
    }
}
