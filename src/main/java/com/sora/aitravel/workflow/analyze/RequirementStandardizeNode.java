package com.sora.aitravel.workflow.analyze;

import com.sora.aitravel.common.utils.CityNameUtils;
import com.sora.aitravel.dto.model.RentalRequirementDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.request.TripAnalyzeRequest;
import com.sora.aitravel.service.impl.TravelRequirementReadyServiceImpl;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 用规则稳定 AI 抽取结果，不调用模型。 */
@Slf4j
@Component
public class RequirementStandardizeNode {

    @Autowired(required = false)
    private TravelRequirementReadyServiceImpl travelRequirementReadyService;

    public void execute(AnalyzeWorkflowContext context) {
        TravelRequirementDTO extracted = context.getExtractedRequirement();
        if (extracted == null) {
            context.setExtractedRequirement(null);
            return;
        }

        TripAnalyzeRequest request = context.getRequest();
        TravelRequirementDTO formInput = request.getFormInput();
        TravelRequirementDTO confirmed = request.getRequirement();

        String departure =
                firstNonBlank(
                        value(formInput, TravelRequirementDTO::getDeparture),
                        value(confirmed, TravelRequirementDTO::getDeparture),
                        extracted.getDeparture());
        String destination =
                CityNameUtils.firstNonBlankCity(
                        request.getSelectedDestination(),
                        value(formInput, TravelRequirementDTO::getDestination),
                        value(confirmed, TravelRequirementDTO::getDestination),
                        extracted.getDestination());
        String routeMode =
                normalizeRouteMode(
                        firstNonBlank(
                                value(formInput, TravelRequirementDTO::getRouteMode),
                                value(confirmed, TravelRequirementDTO::getRouteMode),
                                extracted.getRouteMode()));
        String transportMode =
                normalizeTransportMode(
                        firstNonBlank(
                                value(formInput, TravelRequirementDTO::getTransportMode),
                                value(confirmed, TravelRequirementDTO::getTransportMode),
                                extracted.getTransportMode()));
        String rentalIntent =
                normalizeRentalIntent(
                        firstNonBlank(
                                value(formInput, TravelRequirementDTO::getRentalIntent),
                                value(confirmed, TravelRequirementDTO::getRentalIntent),
                                extracted.getRentalIntent()));
        if (rentalRequired(rentalIntent, formInput, confirmed, extracted)) {
            transportMode = "RENTAL_CAR";
            routeMode = "LANDING_RENTAL_TRIP";
        }
        Integer days =
                firstNonNull(
                        value(formInput, TravelRequirementDTO::getDays),
                        value(confirmed, TravelRequirementDTO::getDays),
                        extracted.getDays());
        Integer peopleCount =
                firstNonNull(
                        value(formInput, TravelRequirementDTO::getPeopleCount),
                        value(confirmed, TravelRequirementDTO::getPeopleCount),
                        extracted.getPeopleCount(),
                        1);

        List<String> routeCities =
                firstNonEmpty(
                        cleanList(value(formInput, TravelRequirementDTO::getRouteCities)),
                        cleanList(value(confirmed, TravelRequirementDTO::getRouteCities)),
                        cleanList(extracted.getRouteCities()));
        routeCities = CityNameUtils.normalizeCityList(routeCities);
        if (routeCities.isEmpty() && hasText(destination)) {
            routeCities = List.of(destination);
        }
        RentalRequirementDTO rentalRequirement =
                firstNonNull(
                        value(formInput, TravelRequirementDTO::getRentalRequirement),
                        value(confirmed, TravelRequirementDTO::getRentalRequirement),
                        extracted.getRentalRequirement());
        standardizeRentalRequirement(rentalRequirement, destination, routeCities);

        TravelRequirementDTO standardized =
                new TravelRequirementDTO(
                        cleanText(departure),
                        cleanText(destination),
                        firstNonBlank(routeMode, "DESTINATION_CITY_TRIP"),
                        normalizeRouteStructure(
                                firstNonBlank(
                                        value(formInput, TravelRequirementDTO::getRouteStructure),
                                        value(confirmed, TravelRequirementDTO::getRouteStructure),
                                        extracted.getRouteStructure())),
                        cleanText(
                                firstNonBlank(
                                        value(formInput, TravelRequirementDTO::getRouteRegion),
                                        value(confirmed, TravelRequirementDTO::getRouteRegion),
                                        extracted.getRouteRegion())),
                        routeCities,
                        transportMode,
                        rentalIntent,
                        rentalRequirement,
                        days,
                        firstNonNull(
                                value(formInput, TravelRequirementDTO::getBudget),
                                value(confirmed, TravelRequirementDTO::getBudget),
                                extracted.getBudget()),
                        normalizeBudgetType(
                                firstNonBlank(
                                        value(formInput, TravelRequirementDTO::getBudgetType),
                                        value(confirmed, TravelRequirementDTO::getBudgetType),
                                        extracted.getBudgetType())),
                        peopleCount,
                        firstNonEmpty(
                                cleanList(value(formInput, TravelRequirementDTO::getPreferences)),
                                cleanList(value(confirmed, TravelRequirementDTO::getPreferences)),
                                cleanList(extracted.getPreferences())),
                        normalizePace(
                                firstNonBlank(
                                        value(formInput, TravelRequirementDTO::getPace),
                                        value(confirmed, TravelRequirementDTO::getPace),
                                        extracted.getPace())),
                        firstNonEmpty(
                                cleanList(value(formInput, TravelRequirementDTO::getAvoidances)),
                                cleanList(value(confirmed, TravelRequirementDTO::getAvoidances)),
                                cleanList(extracted.getAvoidances())),
                        cleanText(
                                firstNonBlank(
                                        value(formInput, TravelRequirementDTO::getTravelDate),
                                        value(confirmed, TravelRequirementDTO::getTravelDate),
                                        extracted.getTravelDate())));

        context.setExtractedRequirement(standardized);
        if (travelRequirementReadyService != null) {
            travelRequirementReadyService.resolveRouteScopeIfMissing(standardized);
        }
        log.info("节点[requirement-standardize]：已用规则标准化 Analyze 抽取结果并补全路线范围。");
    }

    private String normalizeBudgetType(String value) {
        String text = cleanText(value);
        if (text == null || "随便".equals(text) || "不限".equals(text)) {
            return "TOTAL";
        }
        String upper = text.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "TOTAL", "DAILY", "PER_PERSON" -> upper;
            default -> "TOTAL";
        };
    }

    private String normalizePace(String value) {
        String text = cleanText(value);
        if (text == null || "随便".equals(text)) {
            return "NORMAL";
        }
        String upper = text.toUpperCase(Locale.ROOT);
        if ("RELAXED".equals(upper)
                || "EASY".equals(upper)
                || text.contains("轻松")
                || text.contains("不累")) {
            return "LIGHT";
        }
        if ("INTENSIVE".equals(upper) || text.contains("紧凑") || text.contains("多玩")) {
            return "TIGHT";
        }
        return switch (upper) {
            case "LIGHT", "NORMAL", "TIGHT" -> upper;
            default -> "NORMAL";
        };
    }

    private String normalizeRouteMode(String value) {
        String text = cleanText(value);
        if (text == null) {
            return null;
        }
        String upper = text.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "DESTINATION_CITY_TRIP", "ROAD_TRIP", "LANDING_RENTAL_TRIP", "REGION_ROUTE" ->
                    upper;
            default -> null;
        };
    }

    private String normalizeRouteStructure(String value) {
        String text = cleanText(value);
        if (text == null) {
            return "SINGLE_CITY";
        }
        String upper = text.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "SINGLE_CITY", "MULTI_CITY", "LOOP", "ONE_WAY" -> upper;
            default -> "SINGLE_CITY";
        };
    }

    private String normalizeTransportMode(String value) {
        String text = cleanText(value);
        if (text == null) {
            return null;
        }
        String upper = text.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "PUBLIC_TRANSIT", "SELF_DRIVE", "RENTAL_CAR", "MIXED" -> upper;
            default -> null;
        };
    }

    private String normalizeRentalIntent(String value) {
        String text = cleanText(value);
        if (text == null) {
            return null;
        }
        String upper = text.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "NO_RENTAL", "USER_REQUIRED", "CONSIDERING" -> upper;
            case "NEED_RENTAL", "REQUIRED" -> "USER_REQUIRED";
            default -> null;
        };
    }

    private boolean rentalRequired(
            String rentalIntent,
            TravelRequirementDTO formInput,
            TravelRequirementDTO confirmed,
            TravelRequirementDTO extracted) {
        return "USER_REQUIRED".equals(rentalIntent)
                || rentalRequired(value(formInput, TravelRequirementDTO::getRentalRequirement))
                || rentalRequired(value(confirmed, TravelRequirementDTO::getRentalRequirement))
                || rentalRequired(value(extracted, TravelRequirementDTO::getRentalRequirement));
    }

    private boolean rentalRequired(com.sora.aitravel.dto.model.RentalRequirementDTO requirement) {
        return requirement != null && Boolean.TRUE.equals(requirement.getNeedRental());
    }

    private void standardizeRentalRequirement(
            RentalRequirementDTO rentalRequirement, String destination, List<String> routeCities) {
        if (rentalRequirement == null) {
            return;
        }
        String startCity =
                CityNameUtils.firstNonBlankCity(
                        rentalRequirement.getRentalStartCity(),
                        rentalRequirement.getPickupCity(),
                        routeCities == null || routeCities.isEmpty() ? null : routeCities.get(0),
                        destination);
        String endCity =
                CityNameUtils.firstNonBlankCity(
                        rentalRequirement.getRentalEndCity(),
                        rentalRequirement.getReturnCity(),
                        routeCities == null || routeCities.isEmpty()
                                ? null
                                : routeCities.get(routeCities.size() - 1),
                        startCity);
        rentalRequirement.setRentalStartCity(startCity);
        rentalRequirement.setPickupCity(startCity);
        rentalRequirement.setRentalEndCity(endCity);
        rentalRequirement.setReturnCity(endCity);
    }

    private List<String> cleanList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().map(this::cleanText).filter(Objects::nonNull).distinct().toList();
    }

    @SafeVarargs
    private <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    @SafeVarargs
    private final List<String> firstNonEmpty(List<String>... values) {
        for (List<String> value : values) {
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return List.of();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String text = cleanText(value);
            if (text != null) {
                return text;
            }
        }
        return null;
    }

    private <T> T value(TravelRequirementDTO requirement, FieldReader<T> reader) {
        return requirement == null ? null : reader.read(requirement);
    }

    private String cleanText(String value) {
        if (!hasText(value)) {
            return null;
        }
        String text = value.trim();
        return "null".equalsIgnoreCase(text) || "无".equals(text) || "暂无".equals(text) ? null : text;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @FunctionalInterface
    private interface FieldReader<T> {
        T read(TravelRequirementDTO requirement);
    }
}
