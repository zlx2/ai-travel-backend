package com.sora.aitravel.workflow.rentalcontext;

import com.sora.aitravel.dto.model.RentalQuoteOptionDTO;
import com.sora.aitravel.dto.model.RentalStoreDTO;
import com.sora.aitravel.dto.response.RentalQuotePreviewResponse;
import com.sora.aitravel.service.RentalQuoteService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RentalContextQuoteRecommendNode {

    private final RentalQuoteService rentalQuoteService;

    public void execute(RentalContextPreviewWorkflowContext context) {
        RentalQuotePreviewResponse response = rentalQuoteService.preview(context.getRequirement());
        List<RentalQuoteOptionDTO> quoteOptions = response.getQuoteOptions();
        context.setQuoteOptions(quoteOptions == null ? List.of() : quoteOptions.stream()
                .map(option -> applyDynamicPickupPoint(option, context.getMatchedStore()))
                .toList());
    }

    private RentalQuoteOptionDTO applyDynamicPickupPoint(
            RentalQuoteOptionDTO option, RentalStoreDTO store) {
        if (option == null || store == null) {
            return option;
        }
        option.setPickupPoiId(null);
        option.setPickupPoiName(store.getDisplayName());
        option.setPickupAddress(firstNonBlank(store.getAddress(), store.getAmapPoiName()));
        option.setReturnPoiId(null);
        option.setReturnPoiName(store.getDisplayName());
        option.setReturnAddress(firstNonBlank(store.getAddress(), store.getAmapPoiName()));
        option.setPickupMode("DYNAMIC_SERVICE_POINT");
        option.setReturnMode("SAME_SERVICE_POINT");
        Map<String, Object> snapshot =
                option.getPriceSnapshot() == null
                        ? new LinkedHashMap<>()
                        : new LinkedHashMap<>(option.getPriceSnapshot());
        snapshot.put("dynamicPickupStore", store);
        option.setPriceSnapshot(snapshot);
        return option;
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
}
