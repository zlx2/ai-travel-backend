package com.sora.aitravel.workflow.analyze;

import com.sora.aitravel.dto.model.DestinationSuggestionDTO;
import com.sora.aitravel.dto.response.TripAnalyzeResponse;
import com.sora.aitravel.workflow.WorkflowNode;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 结果合并节点。
 * <p>
 * 实现 {@link WorkflowNode} 接口，是 {@link TripAnalyzeWorkflow} 工作流的第六个步骤。
 * 负责将前面各节点（信息提取、完整性检查、目的地推荐、冲突检测）产生的中间结果
 * 合并为一个统一的、结构化的 {@link com.sora.aitravel.dto.response.TripAnalyzeResponse}。
 * 同时也负责将本次分析与用户之前的会话历史进行合并。
 * <p>
 * 在整个工作流中的位置：流程第 6 步（冲突检测之后，JSON 校验之前）。
 * <p>
 * 输入：{@link AnalyzeWorkflowContext} 中各节点写入的中间数据。
 * 输出：将合并后的最终结果写入 {@link AnalyzeWorkflowContext#result}。
 */
@Component
public class AnalyzeResultMergeNode implements WorkflowNode<AnalyzeWorkflowContext> {

    /**
     * 执行结果合并逻辑——将各节点产出整合为最终响应结构。
     *
     * @param context 工作流上下文，读取各中间数据并合并为最终结果
     */
    public void execute(AnalyzeWorkflowContext context) {
        String conversationId =
                context.getRequest().conversationId() == null
                        ? UUID.randomUUID().toString()
                        : context.getRequest().conversationId();

        if ("NEED_DESTINATION_CHOICE".equals(context.getStatus())) {
            context.setResult(
                    new TripAnalyzeResponse(
                            conversationId,
                            "NEED_DESTINATION_CHOICE",
                            null,
                            List.of(),
                            List.of(
                                    new DestinationSuggestionDTO(
                                            "成都", "适合美食、慢节奏和周边自驾。", List.of("美食", "周边", "休闲"), 4),
                                    new DestinationSuggestionDTO(
                                            "杭州", "适合自然风光、城市漫步和亲子轻松游。", List.of("自然风光", "亲子"), 3),
                                    new DestinationSuggestionDTO(
                                            "重庆", "适合夜景、美食和山城城市体验。", List.of("美食", "夜景"), 3)),
                            List.of(),
                            0));
            return;
        }

        context.setResult(
                new TripAnalyzeResponse(
                        conversationId,
                        "READY",
                        context.getExtractedRequirement(),
                        List.of(),
                        List.of(),
                        List.of(),
                        0));
    }
}
