package com.sora.aitravel.workflow.rentalcontext;

import com.sora.aitravel.common.enums.RentalStoreUsageEnum;
import com.sora.aitravel.dto.model.RentalArrivalPointDTO;
import com.sora.aitravel.dto.model.RentalStoreResolveCommand;
import com.sora.aitravel.service.RentalStoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RentalContextStoreResolveNode {

    private final RentalStoreService rentalStoreService;

    public void execute(RentalContextPreviewWorkflowContext context) {
        RentalArrivalPointDTO arrival = context.getArrivalPoint();
        context.setMatchedStore(
                rentalStoreService.resolveRentalStore(
                        new RentalStoreResolveCommand(
                                arrival.getName(), arrival.getCityName(), RentalStoreUsageEnum.PICKUP)));
    }
}
