package com.sora.aitravel.workflow.analyze;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 目的地缺失时，调用真实 LLM 生成候选目的地。 */
@Slf4j
@Component
public class DestinationSuggestNode {

    private final AnalyzeLlmClient llmClient;

    public DestinationSuggestNode(AnalyzeLlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public void execute(AnalyzeWorkflowContext context) {
        log.info("节点[destination-suggest]：调用真实 ChatModel 推荐候选目的地。");
        context.setDestinationSuggestions(
                llmClient.recommendDestinations(
                        context.getCleanInput(), context.getExtractedRequirement()));
    }
}
