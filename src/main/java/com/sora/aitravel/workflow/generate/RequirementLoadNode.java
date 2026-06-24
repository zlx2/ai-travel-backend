package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.service.RentalQuoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 读取 Analyze 阶段已经确认的结构化旅行需求。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RequirementLoadNode {

    private final RentalQuoteService rentalQuoteService;

    public void execute(GenerateWorkflowContext context) {
        TravelRequirementDTO requirement = context.getRequest().getRequirement();
        context.setRequirement(requirement);
        context.setSelectedQuote(context.getRequest().getSelectedQuote());
        if (context.getSelectedQuote() == null && isRentalTrip(requirement)) {
            context.setSelectedQuote(
                    rentalQuoteService.preview(requirement).getQuoteOptions().get(0));
            log.info("节点[requirement-load]：租车报价未传入，已通过租车报价服务补充默认 selectedQuote。");
        }
        log.info(
                "节点[requirement-load]：读取已确认需求，departure={}, destination={}, days={}, peopleCount={}, preferences={}",
                requirement.getDeparture(),
                requirement.getDestination(),
                requirement.getDays(),
                requirement.getPeopleCount(),
                requirement.getPreferences());
    }

    private boolean isRentalTrip(TravelRequirementDTO requirement) {
        return "ROAD_TRIP".equals(requirement.getRouteMode())
                || "LANDING_RENTAL_TRIP".equals(requirement.getRouteMode())
                || "RENTAL_CAR".equals(requirement.getTransportMode())
                || "SELF_DRIVE".equals(requirement.getTransportMode())
                || "USER_REQUIRED".equals(requirement.getRentalIntent())
                || (requirement.getRentalRequirement() != null
                        && Boolean.TRUE.equals(requirement.getRentalRequirement().getNeedRental()));
    }
}
