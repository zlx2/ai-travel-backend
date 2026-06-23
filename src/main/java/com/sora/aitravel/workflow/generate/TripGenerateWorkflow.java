package com.sora.aitravel.workflow.generate;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 结构化行程生成工作流。
 * <p>
 * 负责编排根据用户需求生成完整行程计划的处理流程。调用模型前必须校验
 * departure（出发地）、destination（目的地）、days（天数）等关键参数；
 * 模型输出非法 JSON 时最多修复一次，修复失败直接返回 AI 错误码。
 * <p>
 * 在整个项目中的位置：AI 工作流三大主流程之一（分析 → 生成 → 聊天）。
 * 本工作流接收经过分析或用户直接提供的结构化需求，调用 AI 模型生成
 * 包含每日详细安排的结构化行程计划。
 * <p>
 * 注意：Generate 不负责自动保存行程，持久化必须由用户随后调用 Trip 接口触发。
 * <p>
 * 输入：{@link GenerateWorkflowContext}（包含用户 ID 和生成请求）。
 * 输出：{@link GenerateWorkflowContext}（包含生成的行程计划和校验结果）。
 */
@Component
@RequiredArgsConstructor
public class TripGenerateWorkflow {

    /** 需求校验节点：校验出发地、目的地、天数等必要参数是否合法。 */
    private final RequirementValidateNode requirementValidateNode;

    /** 交通方式判断节点：判断行程更适合公共交通、自驾或混合出行。 */
    private final TravelModeDecisionNode travelModeDecisionNode;

    /** 景点推荐占位节点：先用假数据跑通正式推荐流程。 */
    private final MockScenicSpotRecommendNode mockScenicSpotRecommendNode;

    /** 美食推荐占位节点：先用假数据跑通正式推荐流程。 */
    private final MockFoodRecommendNode mockFoodRecommendNode;

    /** 住宿区域推荐占位节点：先用假数据跑通正式推荐流程。 */
    private final MockHotelAreaRecommendNode mockHotelAreaRecommendNode;

    /** 交通推荐节点：按交通方式补充公共交通或自驾租车点建议。 */
    private final TransportRecommendNode transportRecommendNode;

    /** 推荐上下文提示词构建节点：将结构化推荐资料转换为模型可读上下文。 */
    private final RecommendationPromptBuildNode recommendationPromptBuildNode;

    /** 行程计划生成节点：调用 AI 模型生成每日行程计划。 */
    private final TripPlanGenerateNode tripPlanGenerateNode;

    /** 生成结果 JSON 校验节点：验证模型返回的行程 JSON 结构是否完整合法。 */
    private final GenerateJsonValidateNode generateJsonValidateNode;

    /** 生成结果 JSON 修复节点：对不合法的 JSON 进行修复（最多一次）。 */
    private final GenerateJsonRepairNode generateJsonRepairNode;

    /** 生成结果合并节点：将模型输出转换为标准的响应结构。 */
    private final GenerateResultMergeNode generateResultMergeNode;

    /**
     * 执行完整的行程生成工作流。
     * <p>
     * 按固定顺序依次执行各节点。生成结果不会自动持久化，
     * 用户需要后续调用 Trip 保存接口触发保存。
     *
     * @param context 工作流上下文，包含用户需求参数
     * @return 执行完成后的上下文，包含生成的行程计划和最终响应
     */
    public GenerateWorkflowContext execute(GenerateWorkflowContext context) {
        // Generate 不负责自动保存行程，持久化必须由用户随后调用 Trip 接口触发。
        requirementValidateNode.execute(context);
        travelModeDecisionNode.execute(context);
        mockScenicSpotRecommendNode.execute(context);
        mockFoodRecommendNode.execute(context);
        mockHotelAreaRecommendNode.execute(context);
        transportRecommendNode.execute(context);
        recommendationPromptBuildNode.execute(context);
        tripPlanGenerateNode.execute(context);
        generateJsonValidateNode.execute(context);
        generateJsonRepairNode.execute(context);
        generateResultMergeNode.execute(context);
        return context;
    }
}
