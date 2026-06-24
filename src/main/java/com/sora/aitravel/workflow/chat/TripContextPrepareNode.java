package com.sora.aitravel.workflow.chat;

import org.springframework.stereotype.Component;

/**
 * 行程上下文准备节点。
 *
 * <p>实现 Spring AI Alibaba Graph node 接口，是 {@link AiChatWorkflow} 工作流的第二个步骤。 负责将从数据库加载的原始行程计划 JSON
 * 进行格式化和剪裁， 转换为适合作为 AI 模型输入上下文的格式。可能包括过滤无关字段、 结构化摘要或按时间线整理行程安排等操作。
 *
 * <p>在整个工作流中的位置：聊天流程第 2 步（上下文加载之后，Prompt 构建之前）。
 *
 * <p>输入：{@link ChatWorkflowContext#tripPlanJson}（原始行程计划 JSON）。 输出：处理后的行程上下文字符串（可直接嵌入 Prompt 的文本），
 * 仍通过 {@link ChatWorkflowContext#tripPlanJson} 或中间字段传递。
 */
@Component
public class TripContextPrepareNode {

    /**
     * 执行行程上下文准备逻辑——将行程 JSON 格式化为模型可读的上下文。
     *
     * @param context 工作流上下文，读取原始行程 JSON 并处理为适合 Prompt 的格式
     */
    public void execute(ChatWorkflowContext context) {
        /* TODO prepare tripPlanJson */
    }
}
