package com.sora.aitravel.workflow.rentalcontext;

import static org.assertj.core.api.Assertions.assertThat;

import com.sora.aitravel.common.enums.RentalStoreUsageEnum;
import com.sora.aitravel.dto.model.RentalFeeBreakdownDTO;
import com.sora.aitravel.dto.model.RentalQuoteOptionDTO;
import com.sora.aitravel.dto.model.RentalStoreDTO;
import com.sora.aitravel.dto.model.RentalStoreResolveCommand;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.request.RentalContextPreviewRequest;
import com.sora.aitravel.dto.response.RentalContextPreviewResponse;
import com.sora.aitravel.dto.response.RentalQuotePreviewResponse;
import com.sora.aitravel.service.RentalQuoteService;
import com.sora.aitravel.service.RentalStoreService;
import java.util.List;
import org.junit.jupiter.api.Test;

class RentalContextPreviewWorkflowTest {

    @Test
    void shouldBuildRecommendedRentalContextWithStoreAndPackages() {
        RentalContextPreviewWorkflow workflow =
                new RentalContextPreviewWorkflow(
                        new RentalContextRequirementNode(),
                        new RentalArrivalResolveNode(),
                        new RentalContextStoreResolveNode(new FakeRentalStoreService()),
                        new RentalContextQuoteRecommendNode(new FakeRentalQuoteService()),
                        new RentalContextResultMergeNode());

        TravelRequirementDTO requirement = new TravelRequirementDTO();
        requirement.setDeparture("上海");
        requirement.setDestination("杭州");
        requirement.setDays(3);
        requirement.setPeopleCount(2);
        requirement.setPreferences(List.of("自然风光", "周边游"));

        RentalContextPreviewWorkflowContext context = new RentalContextPreviewWorkflowContext();
        context.setUserId(1L);
        context.setRequest(new RentalContextPreviewRequest(requirement, "杭州萧山国际机场"));

        RentalContextPreviewResponse response = workflow.execute(context).getResult();

        assertThat(response.getRentalRecommended()).isTrue();
        assertThat(response.getReason()).contains("多人同行").contains("多日行程");
        assertThat(response.getArrivalPoint().getName()).isEqualTo("杭州萧山国际机场");
        assertThat(response.getArrivalPoint().getSource()).isEqualTo("USER_PROVIDED");
        assertThat(response.getMatchedStore().getDisplayName()).isEqualTo("PlanGo 杭州萧山机场服务点");
        assertThat(response.getPickupPlan().getMode()).isEqualTo("DELIVERY_PICKUP");
        assertThat(response.getPickupPlan().getDisplayText()).contains("送车接人");
        assertThat(response.getQuoteOptions()).hasSize(3);
        assertThat(response.getRequirement().getTransportMode()).isEqualTo("RENTAL_CAR");
        assertThat(response.getRequirement().getRentalRequirement().getNeedRental()).isTrue();
        assertThat(response.getRequirement().getRentalRequirement().getDeliveryRequired()).isTrue();
        assertThat(response.getRequirement().getRentalRequirement().getDeliveryAddress())
                .isEqualTo("杭州萧山国际机场");
    }

    private static class FakeRentalStoreService implements RentalStoreService {
        @Override
        public RentalStoreDTO resolveRentalStore(RentalStoreResolveCommand command) {
            assertThat(command.getTargetName()).isEqualTo("杭州萧山国际机场");
            assertThat(command.getCityName()).isEqualTo("杭州");
            assertThat(command.getUsage()).isEqualTo(RentalStoreUsageEnum.PICKUP);
            return store();
        }

        @Override
        public RentalStoreDTO resolveRentalStore(
                String targetName, String cityName, RentalStoreUsageEnum usage) {
            return store();
        }

        private RentalStoreDTO store() {
            return RentalStoreDTO.builder()
                    .storeCode("AMAP_TEST")
                    .displayName("PlanGo 杭州萧山机场服务点")
                    .source("AMAP_DYNAMIC")
                    .usage("PICKUP")
                    .amapPoiId("B0TEST")
                    .amapPoiName("萧山机场停车场")
                    .address("萧山机场 P2 停车场")
                    .cityName("杭州市")
                    .distanceMeters(850)
                    .build();
        }
    }

    private static class FakeRentalQuoteService implements RentalQuoteService {
        @Override
        public RentalQuotePreviewResponse preview(TravelRequirementDTO requirement) {
            assertThat(requirement.getRentalRequirement().getNeedRental()).isTrue();
            assertThat(requirement.getRouteMode()).isEqualTo("LANDING_RENTAL_TRIP");
            return RentalQuotePreviewResponse.builder()
                    .routeMode(requirement.getRouteMode())
                    .rentalCity("杭州")
                    .citycode("0571")
                    .quoteOptions(
                            List.of(
                                    quote(1L, "ECONOMY", "经济舒适套餐", 16800),
                                    quote(2L, "SUV", "家庭 SUV 套餐", 29800),
                                    quote(3L, "COMFORT", "品质出行套餐", 26800)))
                    .build();
        }

        @Override
        public RentalQuoteOptionDTO recalculate(
                TravelRequirementDTO requirement, RentalQuoteOptionDTO selectedQuote) {
            return selectedQuote;
        }

        @Override
        public List<RentalQuoteOptionDTO> latestOrderedOptions(int limit) {
            return List.of();
        }

        private RentalQuoteOptionDTO quote(Long id, String code, String name, int dailyFeeCent) {
            return RentalQuoteOptionDTO.builder()
                    .quoteId("Q-" + id)
                    .vehicleGroupId(id)
                    .groupCode(code)
                    .groupName(name)
                    .displayName(name)
                    .rentalDays(3)
                    .feeBreakdown(
                            RentalFeeBreakdownDTO.builder()
                                    .rentalFeeCent(dailyFeeCent * 3)
                                    .totalPriceCent(dailyFeeCent * 3)
                                    .build())
                    .build();
        }
    }
}
