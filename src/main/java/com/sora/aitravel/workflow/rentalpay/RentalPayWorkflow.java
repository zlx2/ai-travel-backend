package com.sora.aitravel.workflow.rentalpay;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RentalPayWorkflow {

    private final RentalPayRequestValidateNode validateNode;
    private final RentalPayProcessNode processNode;

    public RentalPayWorkflowContext execute(RentalPayWorkflowContext context) {
        validateNode.execute(context);
        processNode.execute(context);
        return context;
    }
}
