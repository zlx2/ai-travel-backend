package com.sora.aitravel.workflow.analyze;

import com.sora.aitravel.workflow.WorkflowNode;
import org.springframework.stereotype.Component;

@Component
public class CompletenessCheckNode implements WorkflowNode<AnalyzeWorkflowContext> {
    public void execute(AnalyzeWorkflowContext context) {
        /* TODO check departure/destination/days */
    }
}
