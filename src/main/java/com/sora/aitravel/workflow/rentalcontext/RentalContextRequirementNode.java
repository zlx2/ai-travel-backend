package com.sora.aitravel.workflow.rentalcontext;

import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.dto.model.RentalRequirementDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RentalContextRequirementNode {

    public void execute(RentalContextPreviewWorkflowContext context) {
        if (context.getRequest() == null || context.getRequest().getRequirement() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "租车上下文需求不能为空");
        }
        TravelRequirementDTO requirement = context.getRequest().getRequirement();
        if (requirement.getDays() == null || requirement.getDays() < 1 || requirement.getDays() > 7) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "租车天数必须在 1 到 7 天之间");
        }
        if (isBlank(requirement.getDestination())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "目的地不能为空");
        }

        RentalRequirementDTO rental = requirement.getRentalRequirement();
        if (rental == null) {
            rental = new RentalRequirementDTO();
            requirement.setRentalRequirement(rental);
        }
        rental.setNeedRental(true);
        String rentalStartCity =
                normalizeRentalCity(
                        firstNotBlank(
                                rental.getRentalStartCity(),
                                rental.getPickupCity(),
                                firstRouteCity(requirement),
                                requirement.getDestination()));
        rental.setRentalStartCity(rentalStartCity);
        rental.setPickupCity(normalizeRentalCity(firstNotBlank(rental.getPickupCity(), rentalStartCity)));
        rental.setReturnCity(firstNotBlank(rental.getReturnCity(), rental.getRentalEndCity(), rental.getPickupCity()));
        rental.setRentalEndCity(firstNotBlank(rental.getRentalEndCity(), rental.getReturnCity()));
        rental.setPickupMode(firstNotBlank(rental.getPickupMode(), "DELIVERY"));
        rental.setReturnMode(firstNotBlank(rental.getReturnMode(), rental.getPickupMode()));
        rental.setRentalDays(rental.getRentalDays() == null ? requirement.getDays() : rental.getRentalDays());
        rental.setDeliveryRequired(true);
        rental.setIsOneWay(Boolean.TRUE.equals(rental.getIsOneWay()));

        requirement.setRouteMode(firstNotBlank(requirement.getRouteMode(), "LANDING_RENTAL_TRIP"));
        requirement.setTransportMode("RENTAL_CAR");
        requirement.setRentalIntent("SYSTEM_RECOMMENDED");
        requirement.setPeopleCount(requirement.getPeopleCount() == null ? 2 : requirement.getPeopleCount());
        requirement.setPreferences(ensureRentalPreference(requirement.getPreferences()));

        context.setRequirement(requirement);
        context.setRentalRecommended(true);
        context.setRecommendReason(buildRecommendReason(requirement));
    }

    private List<String> ensureRentalPreference(List<String> preferences) {
        List<String> result = new ArrayList<>();
        if (preferences != null) {
            result.addAll(preferences);
        }
        if (result.stream().noneMatch(item -> item.contains("租车") || item.contains("自驾"))) {
            result.add("租车出行");
        }
        return result;
    }

    private String buildRecommendReason(TravelRequirementDTO requirement) {
        List<String> reasons = new ArrayList<>();
        if (requirement.getPeopleCount() != null && requirement.getPeopleCount() >= 2) {
            reasons.add("多人同行更适合租车分摊交通成本");
        }
        if (requirement.getDays() != null && requirement.getDays() >= 2) {
            reasons.add("多日行程用车更灵活");
        }
        if (requirement.getPreferences() != null
                && requirement.getPreferences().stream()
                        .anyMatch(item -> item.contains("自然") || item.contains("周边") || item.contains("亲子"))) {
            reasons.add("偏好包含周边或自然场景，自驾衔接更顺畅");
        }
        if (reasons.isEmpty()) {
            reasons.add("当前行程可通过送车接人降低到达后的交通衔接成本");
        }
        return String.join("，", reasons);
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
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
        String text = value.trim();
        String[] parts = text.split("[、,，/|；;\\s]+|和|及|与|到|至|\\+");
        for (String part : parts) {
            String city = cleanCity(part);
            if (!isBlank(city)) {
                return city;
            }
        }
        return cleanCity(text);
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
