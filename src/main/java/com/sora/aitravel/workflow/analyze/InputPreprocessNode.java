package com.sora.aitravel.workflow.analyze;

import org.springframework.stereotype.Component;

/**
 * 输入预处理节点。
 *
 * <p>实现 Spring AI Alibaba Graph node 接口，是 {@link TripAnalyzeWorkflow} 工作流的第一个步骤。
 * 负责对用户的原始自然语言输入进行标准化和预处理，如去除多余空格、统一标点符号、 解析常见缩写等，为后续的信息提取节点提供干净的输入数据。
 *
 * <p>在整个工作流中的位置：流程第 1 步（最先执行）。
 *
 * <p>输入：{@link AnalyzeWorkflowContext#request}（用户原始请求）。 输出：将预处理后的数据写回 {@link
 * AnalyzeWorkflowContext#request}（修改原对象或设置规范化字段）。
 */
@Component
public class InputPreprocessNode {

    /**
     * 执行输入预处理逻辑。
     *
     * @param context 工作流上下文，从中读取用户请求并进行规范化处理
     */
    public void execute(AnalyzeWorkflowContext context) {
        /* TODO normalize input */
    }
}
