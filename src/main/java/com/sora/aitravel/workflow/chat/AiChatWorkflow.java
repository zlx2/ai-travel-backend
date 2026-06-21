package com.sora.aitravel.workflow.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * AI 聊天工作流（已保存行程的快捷问答）。
 * <p>
 * 负责编排用户对已保存行程的 AI 智能问答处理流程。一期只支持 TRIP 模式：
 * 读取当前用户拥有的行程计划 JSON 后给出建议，不修改行程，也不保存完整聊天历史。
 * <p>
 * 在整个项目中的位置：AI 工作流三大主流程之一（分析 → 生成 → 聊天）。
 * 本工作流允许用户在已有行程的基础上进行交互式问答，获取行程建议和优化意见。
 * <p>
 * 安全约束：所有权校验必须在加载行程上下文时完成，避免将他人行程内容发送给模型。
 * <p>
 * 输入：{@link ChatWorkflowContext}（包含用户 ID、聊天请求）。
 * 输出：{@link ChatWorkflowContext}（包含模型响应和格式化结果）。
 */
@Component
@RequiredArgsConstructor
public class AiChatWorkflow {

    /** 上下文加载节点：校验用户的行程所有权并加载相关数据。 */
    private final ChatContextLoadNode contextLoadNode;

    /** 行程上下文准备节点：将行程计划 JSON 格式化为模型可理解的上下文。 */
    private final TripContextPrepareNode tripContextPrepareNode;

    /** 提示词构建节点：根据聊天内容和行程上下文组装完整的 Prompt。 */
    private final ChatPromptBuildNode promptBuildNode;

    /** 模型调用节点：调用 AI 大模型（如 DeepSeek）获取回复。 */
    private final ModelCallNode modelCallNode;

    /** 结果格式化节点：校验和格式化模型返回的回复及建议。 */
    private final ChatResultFormatNode resultFormatNode;

    /**
     * 执行完整的 AI 聊天工作流。
     * <p>
     * 按固定顺序依次执行各节点。所有权校验必须在加载行程上下文时完成，
     * 避免将他人行程内容发送给模型。
     *
     * @param context 工作流上下文，包含用户聊天请求和行程数据
     * @return 执行完成后的上下文，包含格式化后的聊天响应结果
     */
    public ChatWorkflowContext execute(ChatWorkflowContext context) {
        // 所有权校验必须在加载行程上下文时完成，避免将他人行程内容发送给模型。
        contextLoadNode.execute(context);
        tripContextPrepareNode.execute(context);
        promptBuildNode.execute(context);
        modelCallNode.execute(context);
        resultFormatNode.execute(context);
        return context;
    }
}
