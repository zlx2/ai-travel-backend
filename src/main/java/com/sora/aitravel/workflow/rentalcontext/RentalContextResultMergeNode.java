package com.sora.aitravel.workflow.rentalcontext;

import com.sora.aitravel.dto.model.RentalPickupPlanDTO;
import com.sora.aitravel.dto.model.RentalStoreDTO;
import com.sora.aitravel.dto.model.RentalTripContextDTO;
import com.sora.aitravel.dto.response.RentalContextPreviewResponse;
import org.springframework.stereotype.Component;

@Component
public class RentalContextResultMergeNode {

    public void execute(RentalContextPreviewWorkflowContext context) {
        RentalStoreDTO store = context.getMatchedStore();
        RentalPickupPlanDTO pickupPlan = buildPickupPlan(store);
        context.setResult(
                RentalContextPreviewResponse.builder()
                        .rentalRecommended(context.getRentalRecommended())
                        .reason(context.getRecommendReason())
                        .requirement(context.getRequirement())
                        .arrivalPoint(context.getArrivalPoint())
                        .matchedStore(store)
                        .pickupPlan(pickupPlan)
                        .rentalTripContext(buildRentalTripContext(context, pickupPlan))
                        .quoteOptions(context.getQuoteOptions())
                        .build());
    }

    private RentalTripContextDTO buildRentalTripContext(
            RentalContextPreviewWorkflowContext context, RentalPickupPlanDTO pickupPlan) {
        return RentalTripContextDTO.builder()
                .arrivalPoint(context.getArrivalPoint())
                .matchedStore(context.getMatchedStore())
                .pickupPlan(pickupPlan)
                .arrivalMode(arrivalMode(context.getArrivalPoint() == null ? null : context.getArrivalPoint().getName()))
                .arrivalTimeRange("到达后取车")
                .routeStructure(
                        context.getRequirement() == null
                                        || context.getRequirement().getRouteStructure() == null
                                ? "城市及周边自驾"
                                : context.getRequirement().getRouteStructure())
                .dailyDrivingLimit("近郊自驾（单日累计约2-4小时）")
                .returnMode("同城还车")
                .returnPoint(context.getArrivalPoint() == null ? null : context.getArrivalPoint().getName())
                .build();
    }

    private String arrivalMode(String arrivalName) {
        if (arrivalName == null || arrivalName.isBlank()) {
            return "还不确定";
        }
        if (arrivalName.contains("机场")) {
            return "机场到达";
        }
        if (arrivalName.contains("站")) {
            return "高铁/火车站到达";
        }
        if (arrivalName.contains("酒店") || arrivalName.contains("宾馆") || arrivalName.contains("民宿")) {
            return "酒店/住宿点出发";
        }
        return "指定地址交车";
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
