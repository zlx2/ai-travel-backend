package com.sora.aitravel.workflow.analyze;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sora.aitravel.common.enums.AnalyzeStatusEnum;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.request.TripAnalyzeRequest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConflictCheckNodeTest {

    @Test
    @DisplayName("轻松节奏和多个偏好不是硬冲突")
    void shouldNotBlockRelaxedTripWithMultiplePreferences() {
        AnalyzeWorkflowContext context = new AnalyzeWorkflowContext();
        TravelRequirementDTO requirement = baseRequirement();
        requirement.setPace("LIGHT");
        requirement.setPreferences(List.of("自然风光", "历史文化", "美食", "亲子", "小众"));
        context.setExtractedRequirement(requirement);

        new ConflictCheckNode().execute(context);

        assertEquals(AnalyzeStatusEnum.READY.name(), context.getStatus());
        assertTrue(context.getConflicts().isEmpty());
    }

    @Test
    @DisplayName("单城市结构被抽到多个路线城市时不拦截生成")
    void shouldNotBlockRouteCityHintsWhenStructureIsSingleCity() {
        AnalyzeWorkflowContext context = new AnalyzeWorkflowContext();
        TravelRequirementDTO requirement = baseRequirement();
        requirement.setRouteStructure("SINGLE_CITY");
        requirement.setRouteCities(List.of("杭州", "千岛湖"));
        context.setExtractedRequirement(requirement);

        new ConflictCheckNode().execute(context);

        assertEquals(AnalyzeStatusEnum.READY.name(), context.getStatus());
        assertTrue(context.getConflicts().isEmpty());
    }

    @Test
    @DisplayName("成都和都江堰租车多城市串联需求不进入冲突态")
    void shouldAnalyzeChengduDujiangyanRentalTripAsReady() {
        String input = "重庆出发，去成都和都江堰玩4天，成都东站下车，想租车自驾，多城市串联，喜欢美食和历史文化，预算在6000元以内。";
        AnalyzeWorkflowContext context = new AnalyzeWorkflowContext();
        context.setRequest(new TripAnalyzeRequest(null, input, null, null, null, null));
        context.setCleanInput(input);

        new InfoExtractNode(null).execute(context);
        new RequirementStandardizeNode().execute(context);
        new ConflictCheckNode().execute(context);

        TravelRequirementDTO requirement = context.getExtractedRequirement();
        assertEquals(AnalyzeStatusEnum.READY.name(), context.getStatus());
        assertTrue(context.getConflicts().isEmpty());
        assertEquals("MULTI_CITY", requirement.getRouteStructure());
        assertEquals(List.of("成都", "都江堰"), requirement.getRouteCities());
        assertEquals("RENTAL_CAR", requirement.getTransportMode());
    }

    private TravelRequirementDTO baseRequirement() {
        TravelRequirementDTO requirement = new TravelRequirementDTO();
        requirement.setDestination("杭州");
        requirement.setRouteMode("DESTINATION_CITY_TRIP");
        requirement.setRouteStructure("SINGLE_CITY");
        requirement.setRouteCities(List.of("杭州"));
        requirement.setDays(3);
        requirement.setBudget(4000);
        requirement.setPeopleCount(1);
        requirement.setPace("NORMAL");
        requirement.setPreferences(List.of("自然风光", "美食"));
        return requirement;
    }
}
