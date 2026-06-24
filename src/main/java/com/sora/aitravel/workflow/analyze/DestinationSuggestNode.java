package com.sora.aitravel.workflow.analyze;

import org.springframework.stereotype.Component;

/**
 * 目的地推荐节点。
 *
 * <p>实现 Spring AI Alibaba Graph node 接口，是 {@link TripAnalyzeWorkflow} 工作流的第四个步骤。
 * 当用户未明确指定目的地时，此节点根据用户提供的偏好（如出行类型、季节、兴趣等） 调用 AI 模型推荐备选目的地，并严格返回三个推荐选项供用户选择。
 *
 * <p>在整个工作流中的位置：流程第 4 步（完整性检查之后，冲突检测之前）。
 *
 * <p>输入：{@link AnalyzeWorkflowContext#rawModelResponse}（已有的行程信息，包含用户偏好）。
 * 输出：将推荐的三个目的地信息合并到上下文中，供后续节点处理。
 */
@Component
public class DestinationSuggestNode {

    /**
     * 执行目的地推荐逻辑——根据用户偏好生成三个推荐目的地。
     *
     * @param context 工作流上下文，读取用户偏好并调用模型获取推荐结果
     */
    public void execute(AnalyzeWorkflowContext context) {
        /* TODO return exactly three suggestions */
    }
}
