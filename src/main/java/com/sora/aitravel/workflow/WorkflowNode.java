package com.sora.aitravel.workflow;

/**
 * AI 工作流节点接口（函数式接口）。
 * <p>
 * 定义工作流中每个节点的通用执行契约。所有具体的 Node 类（如 InputPreprocessNode、ModelCallNode 等）
 * 都实现此接口，通过 {@link #execute(Object)} 方法完成各自的职责。
 * <p>
 * 在整个工作流中的位置：工作流的"积木"单元。每个工作流（如 TripAnalyzeWorkflow、AiChatWorkflow、
 * TripGenerateWorkflow）按顺序编排多个节点实例，形成完整的业务处理流水线。
 * <p>
 * 输入：泛型上下文对象 C（如 AnalyzeWorkflowContext、ChatWorkflowContext、GenerateWorkflowContext）。
 * 输出：无（void）。节点的执行结果直接写入上下文对象中，供后续节点读取。
 *
 * @param <C> 上下文类型，携带该工作流所需的所有数据
 */
@FunctionalInterface
public interface WorkflowNode<C> {

    /**
     * 执行当前节点的业务逻辑。
     *
     * @param context 工作流上下文对象，包含该节点所需的输入数据；节点执行后将结果写入上下文中
     */
    void execute(C context);
}
