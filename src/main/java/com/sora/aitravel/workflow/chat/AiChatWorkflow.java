package com.sora.aitravel.workflow.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 已保存行程的快捷问 AI Workflow。
 *
 * <p>一期只支持 TRIP 模式：读取当前用户拥有的 tripPlanJson 后给出建议，不修改行程，也不保存完整聊天历史。
 */
@Component
@RequiredArgsConstructor
public class AiChatWorkflow {
    private final ChatContextLoadNode contextLoadNode;
    private final TripContextPrepareNode tripContextPrepareNode;
    private final ChatPromptBuildNode promptBuildNode;
    private final ModelCallNode modelCallNode;
    private final ChatResultFormatNode resultFormatNode;

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
