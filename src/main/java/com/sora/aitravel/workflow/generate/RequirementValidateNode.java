package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.workflow.WorkflowNode;
import org.springframework.stereotype.Component;

/**
 * 需求校验节点。
 *
 * <p>实现 {@link WorkflowNode} 接口，是 {@link TripGenerateWorkflow} 工作流的第一个步骤。 负责在校验调用 AI
 * 模型之前，确保用户提供的行程生成参数完整且合法。 必填检查包括：出发地（departure）、目的地（destination）不为空； 出行天数（days）必须在 1-7 天的合理范围内。
 *
 * <p>在整个工作流中的位置：生成流程第 1 步（最先执行）。校验失败将直接抛出异常， 不会继续后续节点。
 *
 * <p>输入：{@link GenerateWorkflowContext#request}（行程生成请求，包含 departure/destination/days）。
 * 输出：校验通过则无副作用；校验失败时抛出 {@link com.sora.aitravel.common.exception.BusinessException}。
 */
@Component
public class RequirementValidateNode implements WorkflowNode<GenerateWorkflowContext> {

    /**
     * 执行需求校验逻辑——确认参数完整且合法。
     *
     * @param context 工作流上下文，从中读取生成请求参数并执行校验
     */
    public void execute(GenerateWorkflowContext context) {
        if (context.getRequest() == null || context.getRequest().requirement() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "行程生成需求不能为空");
        }

        TravelRequirementDTO requirement = context.getRequest().requirement();
        if (requirement.departure() == null || requirement.departure().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "出发地不能为空");
        }
        if (requirement.days() == null || requirement.days() < 1 || requirement.days() > 7) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "行程天数必须在 1 到 7 天之间");
        }
        boolean rentalTrip =
                "ROAD_TRIP".equals(requirement.routeMode())
                        || "LANDING_RENTAL_TRIP".equals(requirement.routeMode())
                        || "RENTAL_CAR".equals(requirement.transportMode())
                        || "USER_REQUIRED".equals(requirement.rentalIntent());
        if (rentalTrip && context.getRequest().selectedQuote() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "租车行程生成必须传入 selectedQuote");
        }
        boolean roadTrip = "ROAD_TRIP".equals(requirement.routeMode());
        if (roadTrip) {
            boolean hasRoadTarget =
                    (requirement.routeCities() != null && !requirement.routeCities().isEmpty())
                            || (requirement.routeRegion() != null
                                    && !requirement.routeRegion().isBlank())
                            || (requirement.destination() != null
                                    && !requirement.destination().isBlank());
            if (!hasRoadTarget) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "自驾路线至少需要目的地、路线区域或途经城市");
            }
            return;
        }
        if (requirement.destination() == null || requirement.destination().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "目的地不能为空");
        }
    }
}
