package com.sora.aitravel.workflow.chat;

import com.sora.aitravel.dto.request.AiChatRequest;
import com.sora.aitravel.dto.response.AiChatResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天工作流上下文。
 *
 * <p>贯穿整个 {@link AiChatWorkflow} 的数据容器，保存 AI 聊天流程中 各节点所需的输入数据以及产生的中间/最终结果。
 *
 * <p>在整个工作流中的位置：{@link AiChatWorkflow} 的输入和输出载体。 工作流入口接收此上下文，依次传递给各 Spring AI Alibaba Graph
 * node，每个节点从中读取输入并写入产出。
 *
 * <p>一期只支持 TRIP 模式：基于当前用户已保存的行程计划进行 AI 问答。
 *
 * <p>输入：{@link #userId}（用户ID）、{@link #request}（聊天请求 DTO）。 中间产物：{@link #tripPlanJson}（行程计划
 * JSON）、{@link #prompt}（构建的提示词）、 {@link #rawModelResponse}（模型原始响应）。 输出：{@link #result}（聊天响应结果 DTO）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatWorkflowContext {

    /** 当前操作用户的 ID，用于权限校验和行程归属确认。 */
    private Long userId;

    /** 用户提交的 AI 聊天请求，包含聊天模式和用户消息。 */
    private AiChatRequest request;

    /** 从数据库加载的用户行程计划 JSON 字符串，作为模型对话的上下文参考。 */
    private String tripPlanJson;

    /** 构建完成的提示词（Prompt），将发送给 AI 模型。 */
    private String prompt;

    /** AI 模型的原始响应文本，由 ModelCallNode 调用模型后填充。 */
    private String rawModelResponse;

    /** 最终格式化的聊天响应结果，供 Controller 层返回给前端。 */
    private AiChatResponse result;
}
