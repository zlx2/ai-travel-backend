package com.sora.aitravel.workflow.rentalpay;

import org.springframework.stereotype.Component;

@Component
public class RentalPayWorkflow {
    private final RentalPayRequestValidateNode validateNode;
    private final RentalPayProcessNode processNode;

    public RentalPayWorkflow(
            RentalPayRequestValidateNode validateNode, RentalPayProcessNode processNode) {
        this.validateNode = validateNode;
        this.processNode = processNode;
    }

    public RentalPayWorkflowContext execute(RentalPayWorkflowContext context) {
        validateNode.execute(context);
        processNode.execute(context);
        return context;
    }
}
