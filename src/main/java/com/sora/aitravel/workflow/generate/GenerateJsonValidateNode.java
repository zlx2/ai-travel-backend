package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.workflow.WorkflowNode;
import org.springframework.stereotype.Component;

@Component
public class GenerateJsonValidateNode implements WorkflowNode<GenerateWorkflowContext> {
    public void execute(GenerateWorkflowContext context) {
        /* TODO validate dailyPlans length */
    }
}
