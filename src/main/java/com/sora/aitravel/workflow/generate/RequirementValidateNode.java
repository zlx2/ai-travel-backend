package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.workflow.WorkflowNode;
import org.springframework.stereotype.Component;

@Component
public class RequirementValidateNode implements WorkflowNode<GenerateWorkflowContext> {
    public void execute(GenerateWorkflowContext context) {
        /* TODO require departure/destination/days 1-7 */
    }
}
