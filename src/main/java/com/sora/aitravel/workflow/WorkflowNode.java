package com.sora.aitravel.workflow;

@FunctionalInterface
public interface WorkflowNode<C> {
    void execute(C context);
}
