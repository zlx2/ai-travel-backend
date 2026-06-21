package com.sora.aitravel.workflow.analyze;

import com.sora.aitravel.workflow.WorkflowNode;
import org.springframework.stereotype.Component;

/**
 * 冲突检测节点。
 * <p>
 * 实现 {@link WorkflowNode} 接口，是 {@link TripAnalyzeWorkflow} 工作流的第五个步骤。
 * 负责检测用户在输入中可能存在的逻辑冲突，例如：出发日期晚于返回日期、
 * 目的地无法从出发地直达且中转时间不够、季节性目的地与时令不符等明显矛盾。
 * <p>
 * 在整个工作流中的位置：流程第 5 步（目的地推荐之后，结果合并之前）。
 * <p>
 * 输入：{@link AnalyzeWorkflowContext#rawModelResponse}（提取的行程信息）。
 * 输出：将冲突检测结果（警告或错误）设置到上下文中，供合并节点处理。
 */
@Component
public class ConflictCheckNode implements WorkflowNode<AnalyzeWorkflowContext> {

    /**
     * 执行冲突检测逻辑——发现并标记用户输入中的逻辑矛盾。
     *
     * @param context 工作流上下文，读取行程信息并检查冲突
     */
    public void execute(AnalyzeWorkflowContext context) {
        /* TODO detect obvious conflicts */
    }
}
