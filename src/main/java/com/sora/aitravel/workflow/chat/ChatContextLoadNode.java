package com.sora.aitravel.workflow.chat;

import com.sora.aitravel.workflow.WorkflowNode;
import org.springframework.stereotype.Component;

/**
 * 聊天上下文加载节点。
 * <p>
 * 实现 {@link WorkflowNode} 接口，是 {@link AiChatWorkflow} 工作流的第一个步骤。
 * 负责校验当前请求模式（要求为 TRIP 模式），并根据用户 ID 和行程 ID
 * 从数据库加载当前用户拥有的行程计划数据。所有权校验在此完成，
 * 防止将他人行程内容发送给 AI 模型。
 * <p>
 * 在整个工作流中的位置：聊天流程第 1 步（最先执行）。
 * <p>
 * 输入：{@link ChatWorkflowContext#request}（聊天请求，包含模式和行程 ID）、
 *       {@link ChatWorkflowContext#userId}（当前用户 ID）。
 * 输出：将加载的行程数据存入 {@link ChatWorkflowContext#tripPlanJson}，
 *       或在校验失败时抛出异常。
 */
@Component
public class ChatContextLoadNode implements WorkflowNode<ChatWorkflowContext> {

    /**
     * 执行上下文加载逻辑——校验模式、加载行程并确认所有权。
     *
     * @param context 工作流上下文，从中读取请求和用户 ID，加载行程数据
     */
    public void execute(ChatWorkflowContext context) {
        /* TODO require mode TRIP and load owned trip */
    }
}
