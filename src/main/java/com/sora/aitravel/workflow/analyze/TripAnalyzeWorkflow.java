package com.sora.aitravel.workflow.analyze;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * AI 行程需求分析工作流。
 * <p>
 * 负责编排用户行程需求分析的完整处理流程。一期采用可调试的串行节点模式，
 * 按固定顺序依次执行各节点，每个节点完成一个独立步骤。
 * <p>
 * 在整个项目中的位置：AI 工作流三大主流程之一（分析 → 生成 → 聊天）。
 * 本工作流接收用户的原始自然语言输入，经过预处理、信息提取、完整性检查、
 * 目的地推荐、冲突检测、结果合并、JSON 校验与修复等一系列步骤，
 * 最终输出结构化的分析结果。
 * <p>
 * 关键设计：JSON 校验失败时才允许进入修复逻辑，且修复最多执行一次；
 * 具体分支由各节点实现内部负责。
 * <p>
 * 输入：{@link AnalyzeWorkflowContext}（包含用户请求和用户 ID）。
 * 输出：{@link AnalyzeWorkflowContext}（包含分析结果 result 和中间产物）。
 */
@Component
@RequiredArgsConstructor
public class TripAnalyzeWorkflow {

    /** 输入预处理节点：规范化用户原始输入。 */
    private final InputPreprocessNode inputPreprocessNode;

    /** 信息提取节点：调用 AI 模型提取关键行程字段。 */
    private final InfoExtractNode infoExtractNode;

    /** 完整性检查节点：检查出发地/目的地/天数等必要字段是否齐全。 */
    private final CompletenessCheckNode completenessCheckNode;

    /** 目的地推荐节点：根据用户偏好推荐备选目的地。 */
    private final DestinationSuggestNode destinationSuggestNode;

    /** 冲突检测节点：检测用户输入中的逻辑冲突（如时间/地点矛盾）。 */
    private final ConflictCheckNode conflictCheckNode;

    /** 结果合并节点：将各节点产出的中间结果合并为统一的响应结构。 */
    private final AnalyzeResultMergeNode resultMergeNode;

    /** JSON 校验节点：验证模型返回的 JSON 是否符合预定义的合法状态结构。 */
    private final AnalyzeJsonValidateNode validateNode;

    /** JSON 修复节点：对非法 JSON 进行修复（最多执行一次）。 */
    private final AnalyzeJsonRepairNode repairNode;

    /**
     * 执行完整的行程需求分析工作流。
     * <p>
     * 按业务约束的固定顺序依次执行各节点。调整顺序前需要同步检查 Prompt
     * 和响应状态合并规则。
     *
     * @param context 工作流上下文，包含用户请求及中间/最终数据
     * @return 执行完成后的上下文，包含最终的分析结果
     */
    public AnalyzeWorkflowContext execute(AnalyzeWorkflowContext context) {
        // 节点顺序是业务约束，调整顺序前需要同步检查 Prompt 和响应状态合并规则。
        inputPreprocessNode.execute(context);
        infoExtractNode.execute(context);
        completenessCheckNode.execute(context);
        destinationSuggestNode.execute(context);
        conflictCheckNode.execute(context);
        resultMergeNode.execute(context);
        validateNode.execute(context);
        repairNode.execute(context);
        return context;
    }
}
