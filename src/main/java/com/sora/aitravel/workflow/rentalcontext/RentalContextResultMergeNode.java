package com.sora.aitravel.workflow.rentalcontext;

import com.sora.aitravel.dto.model.RentalPickupPlanDTO;
import com.sora.aitravel.dto.model.RentalStoreDTO;
import com.sora.aitravel.dto.response.RentalContextPreviewResponse;
import org.springframework.stereotype.Component;

@Component
public class RentalContextResultMergeNode {

    public void execute(RentalContextPreviewWorkflowContext context) {
        RentalStoreDTO store = context.getMatchedStore();
        context.setResult(
                RentalContextPreviewResponse.builder()
                        .rentalRecommended(context.getRentalRecommended())
                        .reason(context.getRecommendReason())
                        .requirement(context.getRequirement())
                        .arrivalPoint(context.getArrivalPoint())
                        .matchedStore(store)
                        .pickupPlan(buildPickupPlan(store))
                        .quoteOptions(context.getQuoteOptions())
                        .build());
    }

    private RentalPickupPlanDTO buildPickupPlan(RentalStoreDTO store) {
        String servicePointName = store == null ? null : store.getDisplayName();
        Integer distanceMeters = store == null ? null : store.getDistanceMeters();
        String distanceText =
                distanceMeters == null
                        ? "附近"
                        : distanceMeters < 1000
                                ? distanceMeters + "米"
                                : String.format("%.1f公里", distanceMeters / 1000.0);
        return RentalPickupPlanDTO.builder()
                .mode("DELIVERY_PICKUP")
                .title("送车接人")
                .servicePointName(servicePointName)
                .distanceMeters(distanceMeters)
                .displayText(
                        "已匹配"
                                + (servicePointName == null ? "附近服务点" : servicePointName)
                                + "，距到达点约"
                                + distanceText
                                + "，可安排工作人员送车接人并现场交车。")
                .build();
    }
}
