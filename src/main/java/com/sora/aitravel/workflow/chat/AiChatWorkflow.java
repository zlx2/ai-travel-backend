package com.sora.aitravel.workflow.chat;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.sora.aitravel.workflow.AlibabaGraphWorkflow;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AiChatWorkflow {

    private final CompiledGraph graph;

    public AiChatWorkflow(
            ChatContextLoadNode contextLoadNode,
            TripContextPrepareNode tripContextPrepareNode,
            ChatPromptBuildNode promptBuildNode,
            ModelCallNode modelCallNode,
            ChatResultFormatNode resultFormatNode) {
        this.graph =
                AlibabaGraphWorkflow.<ChatWorkflowContext>compile(
                        "ai-chat-workflow",
                        List.of(
                                AlibabaGraphWorkflow.step("context-load", contextLoadNode::execute),
                                AlibabaGraphWorkflow.step(
                                        "trip-context-prepare", tripContextPrepareNode::execute),
                                AlibabaGraphWorkflow.step("prompt-build", promptBuildNode::execute),
                                AlibabaGraphWorkflow.step("model-call", modelCallNode::execute),
                                AlibabaGraphWorkflow.step(
                                        "result-format", resultFormatNode::execute)));
    }

    public ChatWorkflowContext execute(ChatWorkflowContext context) {
        return AlibabaGraphWorkflow.invoke(graph, context);
    }
}
