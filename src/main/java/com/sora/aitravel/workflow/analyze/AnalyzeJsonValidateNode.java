package com.sora.aitravel.workflow.analyze;

import com.sora.aitravel.workflow.WorkflowNode;
import org.springframework.stereotype.Component;

@Component
public class AnalyzeJsonValidateNode implements WorkflowNode<AnalyzeWorkflowContext> {
    public void execute(AnalyzeWorkflowContext context) {
        /* TODO validate four legal statuses */
    }
}
