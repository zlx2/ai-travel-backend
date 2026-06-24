package com.sora.aitravel.workflow.analyze;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.sora.aitravel.workflow.AlibabaGraphWorkflow;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TripAnalyzeWorkflow {

    private final CompiledGraph graph;

    public TripAnalyzeWorkflow(
            InputPreprocessNode inputPreprocessNode,
            InfoExtractNode infoExtractNode,
            CompletenessCheckNode completenessCheckNode,
            DestinationSuggestNode destinationSuggestNode,
            ConflictCheckNode conflictCheckNode,
            AnalyzeResultMergeNode resultMergeNode,
            AnalyzeJsonValidateNode validateNode,
            AnalyzeJsonRepairNode repairNode) {
        this.graph =
                AlibabaGraphWorkflow.<AnalyzeWorkflowContext>compile(
                        "trip-analyze-workflow",
                        List.of(
                                AlibabaGraphWorkflow.step(
                                        "input-preprocess", inputPreprocessNode::execute),
                                AlibabaGraphWorkflow.step("info-extract", infoExtractNode::execute),
                                AlibabaGraphWorkflow.step(
                                        "completeness-check", completenessCheckNode::execute),
                                AlibabaGraphWorkflow.step(
                                        "destination-suggest", destinationSuggestNode::execute),
                                AlibabaGraphWorkflow.step(
                                        "conflict-check", conflictCheckNode::execute),
                                AlibabaGraphWorkflow.step("result-merge", resultMergeNode::execute),
                                AlibabaGraphWorkflow.step("json-validate", validateNode::execute),
                                AlibabaGraphWorkflow.step("json-repair", repairNode::execute)));
    }

    public AnalyzeWorkflowContext execute(AnalyzeWorkflowContext context) {
        return AlibabaGraphWorkflow.invoke(graph, context);
    }
}
