package com.sora.aitravel.workflow.generate;

import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.RENTAL_TRIP_CONTEXT;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.REQUEST;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.REQUIREMENT;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.SELECTED_QUOTE;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.request.TripGenerateRequest;
import com.sora.aitravel.workflow.generate.state.TripGraphStateCodec;
import java.util.Map;
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

    public Map<String, Object> execute(OverAllState state) {
        TripGenerateRequest request = TripGraphStateCodec.required(state, REQUEST, TripGenerateRequest.class);
        TravelRequirementDTO requirement = request.getRequirement();
        log.info(
                "节点[requirement-load]：读取已确认需求，departure={}, destination={}, days={}, peopleCount={}, preferences={}",
                requirement.getDeparture(),
                requirement.getDestination(),
                requirement.getDays(),
                requirement.getPeopleCount(),
                requirement.getPreferences());
        return TripGraphStateCodec.patch(
                REQUIREMENT, requirement,
                SELECTED_QUOTE, request.getSelectedQuote(),
                RENTAL_TRIP_CONTEXT, request.getRentalTripContext());
    }
}
