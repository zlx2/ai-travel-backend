package com.sora.aitravel.workflow.analyze;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sora.aitravel.common.enums.AnalyzeStatusEnum;
import com.sora.aitravel.dto.model.RentalRequirementDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.request.TripAnalyzeRequest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequirementStandardizeNodeTest {

    @Test
    @DisplayName("明确租车自驾时覆盖公共交通误抽取，避免进入冲突态")
    void shouldNormalizeRentalTripTransportMode() {
        AnalyzeWorkflowContext context = new AnalyzeWorkflowContext();
        context.setRequest(
                new TripAnalyzeRequest(
                        null,
                        "上海出发，飞到成都玩3天，成都双流机场下飞机，想租车自驾，喜欢自然风光、历史文化和美食，节奏轻松，预算在5000元以内。",
                        null,
                        null,
                        null,
                        null));
        TravelRequirementDTO extracted = new TravelRequirementDTO();
        extracted.setDeparture("上海");
        extracted.setDestination("成都");
        extracted.setRouteMode("DESTINATION_CITY_TRIP");
        extracted.setRouteStructure("SINGLE_CITY");
        extracted.setRouteCities(List.of("成都"));
        extracted.setTransportMode("PUBLIC_TRANSIT");
        extracted.setRentalIntent("USER_REQUIRED");
        extracted.setRentalRequirement(
                new RentalRequirementDTO(
                        true, null, null, null, null, "成都", "成都", null, 3, null, null, null,
                        false));
        extracted.setDays(3);
        extracted.setBudget(5000);
        extracted.setBudgetType("TOTAL");
        extracted.setPeopleCount(1);
        extracted.setPreferences(List.of("自然风光", "历史文化", "美食"));
        extracted.setPace("LIGHT");
        context.setExtractedRequirement(extracted);

        new RequirementStandardizeNode().execute(context);
        new ConflictCheckNode().execute(context);

        TravelRequirementDTO standardized = context.getExtractedRequirement();
        assertEquals("RENTAL_CAR", standardized.getTransportMode());
        assertEquals("LANDING_RENTAL_TRIP", standardized.getRouteMode());
        assertEquals(AnalyzeStatusEnum.READY.name(), context.getStatus());
        assertTrue(context.getConflicts().isEmpty());
    }
}
