package com.sora.aitravel.workflow.analyze;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 调用真实 LLM，从用户输入中抽取结构化旅行需求。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InfoExtractNode {

    private final AnalyzeLlmClient llmClient;

    public void execute(AnalyzeWorkflowContext context) {
        log.info("节点[info-extract]：通过 ChatClient 抽取结构化旅行需求。");
        context.setExtractedRequirement(
                llmClient.extractRequirement(
                        context.getCleanInput(), context.getRequest().getSelectedDestination()));
    }
}
