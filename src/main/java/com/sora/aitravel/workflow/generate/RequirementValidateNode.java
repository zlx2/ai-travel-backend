package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 行程生成需求校验节点，校验失败会直接中断准备阶段。 */
@Slf4j
@Component
public class RequirementValidateNode {

    /**
     * 执行需求校验逻辑——确认参数完整且合法。
     *
     * @param context 工作流上下文，从中读取生成请求参数并执行校验
     */
    public void execute(GenerateWorkflowContext context) {
        if (context.getRequest() == null || context.getRequest().getRequirement() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "行程生成需求不能为空");
        }

        TravelRequirementDTO requirement = context.getRequest().getRequirement();
        if (requirement.getDeparture() == null || requirement.getDeparture().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "出发地不能为空");
        }
        if (requirement.getDays() == null
                || requirement.getDays() < 1
                || requirement.getDays() > 7) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "行程天数必须在 1 到 7 天之间");
        }
        boolean rentalTrip =
                "ROAD_TRIP".equals(requirement.getRouteMode())
                        || "LANDING_RENTAL_TRIP".equals(requirement.getRouteMode())
                        || "RENTAL_CAR".equals(requirement.getTransportMode())
                        || "SELF_DRIVE".equals(requirement.getTransportMode())
                        || "USER_REQUIRED".equals(requirement.getRentalIntent());
        if (rentalTrip && context.getRequest().getSelectedQuote() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "租车行程需要先确认租车报价");
        }
        boolean roadTrip = "ROAD_TRIP".equals(requirement.getRouteMode());
        if (roadTrip) {
            boolean hasRoadTarget =
                    (requirement.getRouteCities() != null
                                    && !requirement.getRouteCities().isEmpty())
                            || (requirement.getRouteRegion() != null
                                    && !requirement.getRouteRegion().isBlank())
                            || (requirement.getDestination() != null
                                    && !requirement.getDestination().isBlank());
            if (!hasRoadTarget) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "自驾路线至少需要目的地、路线区域或途经城市");
            }
            return;
        }
        if (requirement.getDestination() == null || requirement.getDestination().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "目的地不能为空");
        }
    }
}
