package com.sora.aitravel.workflow.rentalquote;

import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.dto.model.RentalRequirementDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import org.springframework.stereotype.Component;

@Component
public class RentalQuoteRequirementValidateNode {
    public void execute(RentalQuotePreviewWorkflowContext context) {
        TravelRequirementDTO requirement = context.getRequirement();
        if (requirement == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "租车报价需求不能为空");
        }
        if (requirement.days() == null || requirement.days() < 1 || requirement.days() > 7) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "租车天数必须在 1 到 7 天之间");
        }

        RentalRequirementDTO rental = requirement.rentalRequirement();
        boolean needRental =
                rental != null && Boolean.TRUE.equals(rental.needRental())
                        || "ROAD_TRIP".equals(requirement.routeMode())
                        || "LANDING_RENTAL_TRIP".equals(requirement.routeMode())
                        || "USER_REQUIRED".equals(requirement.rentalIntent());
        if (!needRental) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "当前需求未选择租车");
        }

        String rentalCity = rental == null ? null : rental.rentalStartCity();
        if (isBlank(rentalCity)) {
            rentalCity =
                    "ROAD_TRIP".equals(requirement.routeMode())
                            ? requirement.departure()
                            : requirement.destination();
        }
        if (isBlank(rentalCity)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "租车城市不能为空");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
