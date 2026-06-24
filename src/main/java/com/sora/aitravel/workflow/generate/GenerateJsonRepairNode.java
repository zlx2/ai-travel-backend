package com.sora.aitravel.workflow.generate;

import org.springframework.stereotype.Component;

/**
 * 生成结果 JSON 修复节点。
 *
 * <p>实现 Spring AI Alibaba Graph node 接口，是 {@link TripGenerateWorkflow} 工作流的第四个步骤。 当 {@link
 * GenerateJsonValidateNode} 检测到行程 JSON 不合法时， 此节点尝试调用 AI 模型对 JSON 进行修复。修复最多执行一次 （由 {@link
 * GenerateWorkflowContext#repairAttempted} 控制），避免无限重试。 若修复失败则直接返回 AI 错误码，不再继续。
 *
 * <p>在整个工作流中的位置：生成流程第 4 步（JSON 校验之后，结果合并之前）。
 *
 * <p>输入：{@link GenerateWorkflowContext#rawModelResponse}（原始或已部分修复的 JSON）、 {@link
 * GenerateWorkflowContext#repairAttempted}（修复标记）。 输出：修复后的 JSON 写回 {@link
 * GenerateWorkflowContext#rawModelResponse}， 并将 {@link GenerateWorkflowContext#repairAttempted} 置为
 * true。
 */
@Component
public class GenerateJsonRepairNode {

    /**
     * 执行 JSON 修复逻辑——在首次校验失败时尝试修复行程 JSON。
     *
     * @param context 工作流上下文，根据校验结果决定是否执行修复
     */
    public void execute(GenerateWorkflowContext context) {
        /* TODO repair once */
    }
}
