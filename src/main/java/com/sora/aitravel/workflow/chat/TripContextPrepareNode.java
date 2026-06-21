package com.sora.aitravel.workflow.chat;

import com.sora.aitravel.workflow.WorkflowNode;
import org.springframework.stereotype.Component;

@Component
public class TripContextPrepareNode implements WorkflowNode<ChatWorkflowContext> {
    public void execute(ChatWorkflowContext context) {
        /* TODO prepare tripPlanJson */
    }
}
