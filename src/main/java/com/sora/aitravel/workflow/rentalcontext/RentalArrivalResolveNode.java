package com.sora.aitravel.workflow.rentalcontext;

import com.sora.aitravel.dto.model.RentalArrivalPointDTO;
import com.sora.aitravel.dto.model.RentalRequirementDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import org.springframework.stereotype.Component;

@Component
public class RentalArrivalResolveNode {

    public void execute(RentalContextPreviewWorkflowContext context) {
        TravelRequirementDTO requirement = context.getRequirement();
        RentalRequirementDTO rental = requirement.getRentalRequirement();
        String arrivalText = context.getRequest().getArrivalText();
        String city = firstNotBlank(rental.getPickupCity(), rental.getRentalStartCity(), requirement.getDestination());
        String name = firstNotBlank(arrivalText, rental.getDeliveryAddress(), defaultArrivalPoint(city, requirement));
        String source = isBlank(arrivalText) ? "SYSTEM_INFERRED" : "USER_PROVIDED";

        context.setArrivalPoint(
                RentalArrivalPointDTO.builder().name(name).cityName(city).source(source).build());
        rental.setDeliveryAddress(name);
    }

    private String defaultArrivalPoint(String city, TravelRequirementDTO requirement) {
        String text = String.join(" ", safe(requirement.getDeparture()), safe(requirement.getDestination()));
        if (text.contains("飞") || text.contains("航班") || text.contains("飞机") || text.contains("机场")) {
            return city + "机场";
        }
        if (text.contains("高铁") || text.contains("火车") || text.contains("动车") || text.contains("车站")) {
            return city + "站";
        }
        return city + "站";
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
