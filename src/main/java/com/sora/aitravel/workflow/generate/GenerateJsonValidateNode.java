package com.sora.aitravel.workflow.generate;

import org.springframework.stereotype.Component;

/**
 * 生成结果 JSON 校验节点。
 *
 * <p>实现 Spring AI Alibaba Graph node 接口，是 {@link TripGenerateWorkflow} 工作流的第三个步骤。 负责验证 AI 模型返回的行程计划
 * JSON 是否符合预期结构，包括： dailyPlans 数组长度是否与请求的天数一致、每日计划中是否包含必要字段、 JSON 格式是否合法等。
 *
 * <p>在整个工作流中的位置：生成流程第 3 步（模型调用之后，修复节点之前）。 若校验通过则后续修复节点自动跳过。
 *
 * <p>输入：{@link GenerateWorkflowContext#rawModelResponse}（模型返回的原始 JSON）。
 * 输出：校验结果（通过/失败）写入上下文，供修复节点决策。
 */
@Component
public class GenerateJsonValidateNode {

    /**
     * 执行 JSON 校验逻辑——验证行程计划 JSON 的结构和内容完整性。
     *
     * @param context 工作流上下文，读取模型响应 JSON 并执行校验
     */
    public void execute(GenerateWorkflowContext context) {
        /* TODO validate dailyPlans length */
    }
}
