package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.dto.model.TravelRequirementDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 读取 Analyze 阶段已经确认的结构化旅行需求。 */
@Slf4j
@Component
public class RequirementLoadNode {

    public void execute(GenerateWorkflowContext context) {
        TravelRequirementDTO requirement = context.getRequest().getRequirement();
        context.setRequirement(requirement);
        context.setSelectedQuote(context.getRequest().getSelectedQuote());
        context.setRentalTripContext(context.getRequest().getRentalTripContext());
        log.info(
                "节点[requirement-load]：读取已确认需求，departure={}, destination={}, days={}, peopleCount={}, preferences={}",
                requirement.getDeparture(),
                requirement.getDestination(),
                requirement.getDays(),
                requirement.getPeopleCount(),
                requirement.getPreferences());
    }
}
