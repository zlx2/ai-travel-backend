package com.sora.aitravel.workflow.analyze;

import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.request.TripAnalyzeRequest;
import com.sora.aitravel.dto.response.TripAnalyzeResponse;
import lombok.Data;

/**
 * 分析工作流上下文。
 *
 * <p>贯穿整个 {@link TripAnalyzeWorkflow} 的数据容器，保存行程需求分析流程中 各节点所需的输入数据以及产生的中间/最终结果。
 *
 * <p>在整个工作流中的位置：{@link TripAnalyzeWorkflow} 的输入和输出载体。 工作流入口接收此上下文，依次传递给各 Spring AI Alibaba Graph
 * node，每个节点从中读取输入并写入产出。
 *
 * <p>输入：{@link #userId}（用户ID）、{@link #request}（分析请求 DTO）。 中间产物：{@link #rawModelResponse}（模型原始响应）。
 * 输出：{@link #result}（分析结果 DTO）。 标记：{@link #repairAttempted}（是否已执行过一次 JSON 修复）。
 */
@Data
public class AnalyzeWorkflowContext {

    /** 当前操作用户的 ID，用于权限校验和数据归属。 */
    private Long userId;

    /** 用户提交的行程需求分析请求，包含出发地、目的地、天数等原始输入。 */
    private TripAnalyzeRequest request;

    /** AI 模型的原始响应文本（JSON 字符串），由 InfoExtractNode 等调用模型后填充。 */
    private String rawModelResponse;

    /** 最小可跑通版本提取出的结构化旅行需求。 */
    private TravelRequirementDTO extractedRequirement;

    /** 分析状态：READY、NEED_DESTINATION_CHOICE 等。 */
    private String status;

    /** 最终的结构化分析结果，供 Controller 层返回给前端。 */
    private TripAnalyzeResponse result;

    /** 标记是否已经执行过一次 JSON 修复。若为 true 则不再重复修复，避免死循环。 */
    private boolean repairAttempted;
}
