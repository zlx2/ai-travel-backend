package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.service.RentalQuoteService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 读取 Analyze 阶段已经确认的结构化旅行需求。 */
@Slf4j
@Component
public class RequirementLoadNode {

    private final RentalQuoteService rentalQuoteService;

    public RequirementLoadNode(RentalQuoteService rentalQuoteService) {
        this.rentalQuoteService = rentalQuoteService;
    }

    public void execute(GenerateWorkflowContext context) {
        TravelRequirementDTO requirement = context.getRequest().requirement();
        context.setRequirement(requirement);
        context.setSelectedQuote(context.getRequest().selectedQuote());
        if (context.getSelectedQuote() == null && isRentalTrip(requirement)) {
            context.setSelectedQuote(rentalQuoteService.preview(requirement).quoteOptions().get(0));
            log.info("节点[requirement-load]：租车报价未传入，已通过租车报价服务补充默认 selectedQuote。");
        }
        log.info(
                "节点[requirement-load]：读取已确认需求，departure={}, destination={}, days={}, peopleCount={}, preferences={}",
                requirement.departure(),
                requirement.destination(),
                requirement.days(),
                requirement.peopleCount(),
                requirement.preferences());
    }

    private boolean isRentalTrip(TravelRequirementDTO requirement) {
        return "ROAD_TRIP".equals(requirement.routeMode())
                || "LANDING_RENTAL_TRIP".equals(requirement.routeMode())
                || "RENTAL_CAR".equals(requirement.transportMode())
                || "SELF_DRIVE".equals(requirement.transportMode())
                || "USER_REQUIRED".equals(requirement.rentalIntent())
                || (requirement.rentalRequirement() != null
                        && Boolean.TRUE.equals(requirement.rentalRequirement().needRental()));
    }
}
