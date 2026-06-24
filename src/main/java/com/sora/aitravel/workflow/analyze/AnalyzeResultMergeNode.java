package com.sora.aitravel.workflow.analyze;

import com.sora.aitravel.dto.model.ConflictDTO;
import com.sora.aitravel.dto.model.DestinationSuggestionDTO;
import com.sora.aitravel.dto.model.QuestionDTO;
import com.sora.aitravel.dto.response.TripAnalyzeResponse;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 将各节点产物组装成前端可展示的 Analyze 响应。 */
@Slf4j
@Component
public class AnalyzeResultMergeNode {

    public void execute(AnalyzeWorkflowContext context) {
        String conversationId =
                context.getRequest().getConversationId() == null
                        ? UUID.randomUUID().toString()
                        : context.getRequest().getConversationId();

        List<QuestionDTO> questions = emptyIfNull(context.getQuestions());
        List<DestinationSuggestionDTO> suggestions =
                emptyIfNull(context.getDestinationSuggestions());
        List<ConflictDTO> conflicts = emptyIfNull(context.getConflicts());
        int askRound =
                context.getRequest().getExtraAnswers() == null
                        ? 0
                        : context.getRequest().getExtraAnswers().size();

        context.setResult(
                new TripAnalyzeResponse(
                        conversationId,
                        context.getStatus(),
                        context.getExtractedRequirement(),
                        questions,
                        suggestions,
                        conflicts,
                        askRound));
        log.info("节点[result-merge]：已组装分析结果，status={}", context.getStatus());
    }

    private <T> List<T> emptyIfNull(List<T> value) {
        return value == null ? List.of() : value;
    }
}
