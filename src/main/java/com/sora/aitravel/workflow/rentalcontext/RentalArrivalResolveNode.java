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
        String city =
                normalizeRentalCity(
                        firstNotBlank(
                                rental.getPickupCity(),
                                rental.getRentalStartCity(),
                                firstRouteCity(requirement),
                                requirement.getDestination()));
        String name = firstNotBlank(arrivalText, rental.getDeliveryAddress(), defaultArrivalPoint(city, requirement));
        String source = isBlank(arrivalText) ? "SYSTEM_INFERRED" : "USER_PROVIDED";

        context.setArrivalPoint(
                RentalArrivalPointDTO.builder().name(name).cityName(city).source(source).build());
        rental.setDeliveryAddress(name);
    }

    private String firstRouteCity(TravelRequirementDTO requirement) {
        return requirement.getRouteCities() == null || requirement.getRouteCities().isEmpty()
                ? null
                : requirement.getRouteCities().get(0);
    }

    private String normalizeRentalCity(String value) {
        if (isBlank(value)) {
            return value;
        }
        String[] parts = value.trim().split("[、,，/|；;\\s]+|和|及|与|到|至|\\+");
        for (String part : parts) {
            String city = cleanCity(part);
            if (!isBlank(city)) {
                return city;
            }
        }
        return cleanCity(value);
    }

    private String cleanCity(String value) {
        if (isBlank(value)) {
            return null;
        }
        String city =
                value.replaceAll("(国际机场|机场|高铁站|火车站|动车站|汽车站|东站|西站|南站|北站|站)$", "")
                        .replaceAll("(市|地区)$", "")
                        .trim();
        return city.isBlank() ? null : city;
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
