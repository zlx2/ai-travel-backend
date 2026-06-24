package com.sora.aitravel.workflow.analyze;

import org.springframework.stereotype.Component;

/**
 * JSON 修复节点。
 *
 * <p>实现 Spring AI Alibaba Graph node 接口，是 {@link TripAnalyzeWorkflow} 工作流的第八个（最后）步骤。 当 {@link
 * AnalyzeJsonValidateNode} 检测到 JSON 不合法时，此节点尝试调用模型 对 JSON 进行修复。修复最多执行一次（由 {@link
 * AnalyzeWorkflowContext#repairAttempted} 控制），避免无限重试。
 *
 * <p>在整个工作流中的位置：流程第 8 步（最后执行）。若 JSON 合法则本节点跳过。
 *
 * <p>输入：{@link AnalyzeWorkflowContext#rawModelResponse}（原始的或已部分修复的 JSON）。 {@link
 * AnalyzeWorkflowContext#repairAttempted}（是否已修复过的标记）。 输出：修复后的 JSON 写回 {@link
 * AnalyzeWorkflowContext#rawModelResponse}， 并将 {@link AnalyzeWorkflowContext#repairAttempted} 置为
 * true。
 */
@Component
public class AnalyzeJsonRepairNode {

    /**
     * 执行 JSON 修复逻辑——在首次校验失败时尝试修复 JSON 格式。
     *
     * @param context 工作流上下文，根据校验结果决定是否执行修复
     */
    public void execute(AnalyzeWorkflowContext context) {
        /* TODO repair at most once */
    }
}
