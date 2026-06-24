package com.sora.aitravel.workflow.rentalquote;

import com.sora.aitravel.dto.model.RentalFeeBreakdownDTO;
import com.sora.aitravel.dto.model.RentalQuoteOptionDTO;
import com.sora.aitravel.dto.model.RentalRequirementDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.response.RentalQuotePreviewResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 租车业务未接入前使用的模拟报价工厂。 */
@Component
public class MockRentalQuoteFactory {

    public RentalQuotePreviewResponse preview(TravelRequirementDTO requirement) {
        String rentalCity = resolveRentalCity(requirement);
        String routeMode = normalizeRouteMode(requirement);
        int rentalDays = resolveRentalDays(requirement);

        List<RentalQuoteOptionDTO> options =
                List.of(
                        quote(
                                "MOCK-ECONOMY",
                                routeMode,
                                rentalCity,
                                "ECONOMY_SEDAN",
                                "经济型轿车",
                                "大众朗逸/丰田卡罗拉同组",
                                "SEDAN",
                                "GASOLINE",
                                5,
                                rentalDays,
                                16800),
                        quote(
                                "MOCK-COMFORT",
                                routeMode,
                                rentalCity,
                                "COMFORT_SEDAN",
                                "舒适型轿车",
                                "丰田凯美瑞/大众帕萨特同组",
                                "SEDAN",
                                "GASOLINE",
                                5,
                                rentalDays,
                                22800),
                        quote(
                                "MOCK-SUV",
                                routeMode,
                                rentalCity,
                                "COMFORT_SUV",
                                "舒适型 SUV",
                                "本田 CR-V/丰田 RAV4 同组",
                                "SUV",
                                "GASOLINE",
                                5,
                                rentalDays,
                                26800));

        return new RentalQuotePreviewResponse(routeMode, rentalCity, "mock-citycode", options);
    }

    public RentalQuoteOptionDTO defaultQuote(TravelRequirementDTO requirement) {
        return preview(requirement).quoteOptions().get(0);
    }

    private RentalQuoteOptionDTO quote(
            String quoteId,
            String routeMode,
            String rentalCity,
            String groupCode,
            String groupName,
            String displayName,
            String vehicleClass,
            String energyType,
            Integer seats,
            Integer rentalDays,
            Integer dailyRentalFeeCent) {
        RentalFeeBreakdownDTO fee = fee(rentalDays, dailyRentalFeeCent);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("source", "mock_rental_quote");
        snapshot.put("mock", true);
        snapshot.put("dailyRentalFeeCent", dailyRentalFeeCent);
        snapshot.put("rentalDays", rentalDays);
        snapshot.put("note", "租车业务暂未接入，当前报价仅用于前后端联调。");

        return new RentalQuoteOptionDTO(
                quoteId,
                routeMode,
                rentalCity,
                "mock-citycode",
                "mock-adcode",
                Math.abs((long) quoteId.hashCode()),
                groupCode,
                groupName,
                displayName,
                vehicleClass,
                energyType,
                seats,
                seats,
                100001L,
                rentalCity + "中心取车点",
                rentalCity + "核心商圈模拟取车点",
                100002L,
                rentalCity + "中心还车点",
                rentalCity + "核心商圈模拟还车点",
                "CITY_POI",
                "CITY_POI",
                rentalDays,
                false,
                Math.abs((long) (quoteId + "-template").hashCode()),
                fee,
                snapshot);
    }

    private RentalFeeBreakdownDTO fee(Integer rentalDays, Integer dailyRentalFeeCent) {
        int days = rentalDays == null ? 1 : rentalDays;
        int rentalFee = dailyRentalFeeCent * days;
        int serviceFee = 2500 * days;
        int prepareFee = 2000;
        int total = rentalFee + serviceFee + prepareFee;
        return new RentalFeeBreakdownDTO(
                rentalFee,
                serviceFee,
                prepareFee,
                0,
                0,
                total,
                300000,
                200000,
                650);
    }

    private String resolveRentalCity(TravelRequirementDTO requirement) {
        RentalRequirementDTO rental = requirement == null ? null : requirement.rentalRequirement();
        if (rental != null && hasText(rental.rentalStartCity())) {
            return rental.rentalStartCity();
        }
        if (requirement != null && "ROAD_TRIP".equals(requirement.routeMode())) {
            return firstNonBlank(requirement.departure(), requirement.destination(), "模拟城市");
        }
        return firstNonBlank(
                requirement == null ? null : requirement.destination(),
                requirement == null ? null : requirement.departure(),
                "模拟城市");
    }

    private String normalizeRouteMode(TravelRequirementDTO requirement) {
        return requirement == null || !hasText(requirement.routeMode())
                ? "LANDING_RENTAL_TRIP"
                : requirement.routeMode();
    }

    private int resolveRentalDays(TravelRequirementDTO requirement) {
        RentalRequirementDTO rental = requirement == null ? null : requirement.rentalRequirement();
        if (rental != null && rental.rentalDays() != null) {
            return rental.rentalDays();
        }
        return requirement == null || requirement.days() == null ? 1 : requirement.days();
    }

    private String firstNonBlank(String first, String second, String fallback) {
        if (hasText(first)) {
            return first;
        }
        if (hasText(second)) {
            return second;
        }
        return fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
