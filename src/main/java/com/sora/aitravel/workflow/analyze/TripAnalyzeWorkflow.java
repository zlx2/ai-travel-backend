package com.sora.aitravel.workflow.analyze;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * AI 需求分析的顺序 Workflow。
 *
 * <p>一期有意采用可调试的串行节点。JSON 校验失败时才允许进入修复逻辑，且修复最多执行一次；具体分支由节点实现负责。
 */
@Component
@RequiredArgsConstructor
public class TripAnalyzeWorkflow {
    private final InputPreprocessNode inputPreprocessNode;
    private final InfoExtractNode infoExtractNode;
    private final CompletenessCheckNode completenessCheckNode;
    private final DestinationSuggestNode destinationSuggestNode;
    private final ConflictCheckNode conflictCheckNode;
    private final AnalyzeResultMergeNode resultMergeNode;
    private final AnalyzeJsonValidateNode validateNode;
    private final AnalyzeJsonRepairNode repairNode;

    public AnalyzeWorkflowContext execute(AnalyzeWorkflowContext context) {
        // 节点顺序是业务约束，调整顺序前需要同步检查 Prompt 和响应状态合并规则。
        inputPreprocessNode.execute(context);
        infoExtractNode.execute(context);
        completenessCheckNode.execute(context);
        destinationSuggestNode.execute(context);
        conflictCheckNode.execute(context);
        resultMergeNode.execute(context);
        validateNode.execute(context);
        repairNode.execute(context);
        return context;
    }
}
