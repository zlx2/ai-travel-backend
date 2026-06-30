package com.sora.aitravel.workflow.generate;

import static org.assertj.core.api.Assertions.assertThat;

import com.sora.aitravel.dto.model.RentalArrivalPointDTO;
import com.sora.aitravel.dto.model.RentalFeeBreakdownDTO;
import com.sora.aitravel.dto.model.RentalPickupPlanDTO;
import com.sora.aitravel.dto.model.RentalQuoteOptionDTO;
import com.sora.aitravel.dto.model.RentalStoreDTO;
import com.sora.aitravel.dto.model.RentalTripContextDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.model.TripPlanDTO;
import com.sora.aitravel.dto.request.TripGenerateRequest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GenerateResultMergeNodeRentalTest {

    @Test
    void shouldMergeLockedPlanAndIncludeRentalCostWithoutInjectingNodes() {
        TravelRequirementDTO requirement = new TravelRequirementDTO();
        requirement.setDeparture("上海");
        requirement.setDestination("杭州");
        requirement.setDays(3);
        requirement.setPeopleCount(2);

        TripGenerateRequest request = new TripGenerateRequest();
        request.setConversationId("conv-1");
        request.setRequirement(requirement);
        request.setSelectedQuote(
                RentalQuoteOptionDTO.builder()
                        .displayName("舒适型轿车")
                        .feeBreakdown(
                                RentalFeeBreakdownDTO.builder().totalPriceCent(47600).build())
                        .build());
        request.setRentalTripContext(
                RentalTripContextDTO.builder()
                        .arrivalPoint(
                                RentalArrivalPointDTO.builder()
                                        .name("杭州萧山国际机场")
                                        .cityName("杭州")
                                        .build())
                        .matchedStore(
                                RentalStoreDTO.builder()
                                        .displayName("杭州萧山国际机场推荐取车点")
                                        .lng("120.433025")
                                        .lat("30.233625")
                                        .build())
                        .pickupPlan(
                                RentalPickupPlanDTO.builder()
                                        .title("送车接人")
                                        .displayText("机场附近送车接人并现场交车。")
                                        .build())
                        .arrivalTimeRange("上午")
                        .returnMode("同城还车")
                        .returnPoint("杭州萧山国际机场")
                        .build());

        TripPlanDTO.Spot firstSight = new TripPlanDTO.Spot();
        firstSight.setName("西湖");
        firstSight.setType("SCENIC");
        firstSight.setOrder(1);

        TripPlanDTO.DailyPlan day = new TripPlanDTO.DailyPlan();
        day.setDay(1);
        day.setTheme("西湖轻松游");
        day.setSpots(new ArrayList<>(List.of(firstSight)));
        day.setEstimatedCost(new TripPlanDTO.EstimatedCost(0, 120, 30, 150, null, null, null, true));

        GenerateWorkflowContext context = new GenerateWorkflowContext();
        context.setRequest(request);
        context.setRequirement(requirement);
        context.setSelectedQuote(request.getSelectedQuote());
        context.setRentalTripContext(request.getRentalTripContext());
        context.setLockedDailyPlans(List.of(day));

        new GenerateResultMergeNode().execute(context);

        TripPlanDTO result = context.getResult().getTripPlan();
        assertThat(result.getDailyPlans().get(0).getSpots()).hasSize(1);
        assertThat(result.getDailyPlans().get(0).getSpots().get(0).getType()).isEqualTo("SCENIC");
        assertThat(result.getDailyPlans().get(0).getSpots().get(0).getOrder()).isEqualTo(1);
        assertThat(result.getBudgetSummary().getTransportCost()).isEqualTo(506);
        assertThat(result.getBudgetSummary().getTotalEstimatedCost()).isEqualTo(626);
    }
}
