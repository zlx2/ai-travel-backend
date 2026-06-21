package com.sora.aitravel.workflow.analyze;

import com.sora.aitravel.workflow.WorkflowNode;
import org.springframework.stereotype.Component;

@Component
public class InputPreprocessNode implements WorkflowNode<AnalyzeWorkflowContext> {
    public void execute(AnalyzeWorkflowContext context) {
        /* TODO normalize input */
    }
}
