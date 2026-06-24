package com.sora.aitravel.workflow.rentalquote;

import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import org.springframework.stereotype.Component;

@Component
public class RentalQuoteRequirementValidateNode {
    public void execute(RentalQuotePreviewWorkflowContext context) {
        TravelRequirementDTO requirement = context.getRequirement();
        if (requirement == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "租车报价需求不能为空");
        }
        if (requirement.getDays() == null
                || requirement.getDays() < 1
                || requirement.getDays() > 7) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "租车天数必须在 1 到 7 天之间");
        }

        String rentalCity =
                "ROAD_TRIP".equals(requirement.getRouteMode())
                        ? requirement.getDeparture()
                        : requirement.getDestination();
        if (isBlank(rentalCity) && isBlank(requirement.getDeparture())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "租车城市不能为空");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
