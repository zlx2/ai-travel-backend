package com.sora.aitravel.workflow.chat;

import org.springframework.stereotype.Component;

/**
 * 聊天提示词构建节点。
 *
 * <p>实现 Spring AI Alibaba Graph node 接口，是 {@link AiChatWorkflow} 工作流的第三个步骤。
 * 负责将用户的聊天消息与已加载的行程上下文结合，使用模板引擎渲染出完整的 AI 提示词（Prompt），引导模型基于当前行程给出针对性的回复和建议。
 *
 * <p>在整个工作流中的位置：聊天流程第 3 步（上下文准备之后，模型调用之前）。
 *
 * <p>输入：{@link ChatWorkflowContext#request}（用户消息内容）、 {@link
 * ChatWorkflowContext#tripPlanJson}（行程上下文）。 输出：构建好的提示词字符串写入 {@link ChatWorkflowContext#prompt}。
 */
@Component
public class ChatPromptBuildNode {

    /**
     * 执行提示词构建逻辑——组装用户消息和行程上下文为完整 Prompt。
     *
     * @param context 工作流上下文，读取用户消息和行程数据并渲染 Prompt
     */
    public void execute(ChatWorkflowContext context) {
        /* TODO render prompt */
    }
}
