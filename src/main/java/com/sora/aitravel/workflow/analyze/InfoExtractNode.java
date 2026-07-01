package com.sora.aitravel.workflow.analyze;

import com.sora.aitravel.dto.model.RentalRequirementDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 调用真实 LLM，从用户输入中抽取结构化旅行需求。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InfoExtractNode {

    private final AnalyzeLlmClient llmClient;

    public void execute(AnalyzeWorkflowContext context) {
        TravelRequirementDTO ruleExtracted = ruleExtract(context);
        if (ruleExtracted != null) {
            log.info("节点[info-extract]：规则抽取旅行需求成功，跳过 AI。");
            context.setExtractedRequirement(ruleExtracted);
            return;
        }
        log.info("节点[info-extract]：通过 ChatClient 抽取结构化旅行需求。");
        context.setExtractedRequirement(
                llmClient.extractRequirement(
                        context.getCleanInput(), context.getRequest().getSelectedDestination()));
    }

    private TravelRequirementDTO ruleExtract(AnalyzeWorkflowContext context) {
        String input = context.getCleanInput();
        if (input == null || input.isBlank()) {
            return null;
        }
        String destination = firstNonBlank(
                context.getRequest().getSelectedDestination(),
                match(input, "去([\\u4e00-\\u9fa5A-Za-z]{2,8})(?:玩|旅游|旅行|自驾|游)"),
                match(input, "到([\\u4e00-\\u9fa5A-Za-z]{2,8})(?:玩|旅游|旅行|自驾|游)"),
                match(input, "飞到([\\u4e00-\\u9fa5A-Za-z]{2,8})"));
        Integer days = numberBefore(input, "天");
        Integer budget = budget(input);
        if (destination == null || days == null) {
            return null;
        }
        boolean rental = containsAny(input, "租车", "自驾", "开车");
        TravelRequirementDTO requirement = new TravelRequirementDTO();
        requirement.setDeparture(match(input, "^([\\u4e00-\\u9fa5A-Za-z]{2,8})出发"));
        List<String> routeCities = routeCities(destination, input);
        String primaryDestination = routeCities.isEmpty() ? destination : routeCities.get(0);
        requirement.setDestination(primaryDestination);
        requirement.setRouteMode(rental ? "LANDING_RENTAL_TRIP" : "DESTINATION_CITY_TRIP");
        requirement.setRouteStructure(routeCities.size() > 1 || containsAny(input, "多城市", "串联") ? "MULTI_CITY" : "SINGLE_CITY");
        requirement.setRouteCities(routeCities.isEmpty() ? List.of(primaryDestination) : routeCities);
        requirement.setTransportMode(rental ? "RENTAL_CAR" : null);
        requirement.setRentalIntent(rental ? "USER_REQUIRED" : null);
        if (rental) {
            RentalRequirementDTO rentalRequirement = new RentalRequirementDTO();
            rentalRequirement.setNeedRental(true);
            rentalRequirement.setPickupCity(primaryDestination);
            rentalRequirement.setReturnCity(routeCities.isEmpty() ? primaryDestination : routeCities.get(routeCities.size() - 1));
            rentalRequirement.setRentalDays(days);
            requirement.setRentalRequirement(rentalRequirement);
        }
        requirement.setDays(days);
        requirement.setBudget(budget);
        requirement.setBudgetType("TOTAL");
        requirement.setPeopleCount(numberBefore(input, "人"));
        requirement.setPreferences(preferences(input));
        requirement.setPace(containsAny(input, "轻松", "不要太累", "不累") ? "LIGHT" : null);
        requirement.setAvoidances(containsAny(input, "不要太累", "不累") ? List.of("不要太累") : List.of());
        return requirement;
    }

    private List<String> routeCities(String destination, String input) {
        List<String> cities = new ArrayList<>();
        if (destination != null) {
            for (String part : destination.split("[、,，/|；;\\s]+|和|及|与|到|至")) {
                String city = cleanCity(part);
                if (city != null && !cities.contains(city)) {
                    cities.add(city);
                }
            }
        }
        String arrivalCity = match(input, "([\\u4e00-\\u9fa5A-Za-z]{2,8})(?:东站|西站|南站|北站|站|机场|客运站|汽车站)(?:下车|落地|到达)");
        String cleanArrivalCity = cleanCity(arrivalCity);
        if (cleanArrivalCity != null && !cities.contains(cleanArrivalCity)) {
            cities.add(0, cleanArrivalCity);
        }
        return cities;
    }

    private String cleanCity(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim()
                .replaceAll("(东站|西站|南站|北站|站|机场|客运站|汽车站)$", "")
                .replaceAll("(?<=[\\u4e00-\\u9fa5]{2})(东|西|南|北)$", "")
                .replaceAll("(市|地区)$", "");
        return text.isBlank() ? null : text;
    }

    private List<String> preferences(String input) {
        List<String> values = new ArrayList<>();
        if (containsAny(input, "自然", "风光", "山水")) values.add("自然风光");
        if (containsAny(input, "历史", "文化", "古迹", "博物馆")) values.add("历史文化");
        if (containsAny(input, "美食", "吃")) values.add("美食");
        if (containsAny(input, "亲子", "孩子")) values.add("亲子");
        if (containsAny(input, "租车", "自驾")) values.add("租车出行");
        return values;
    }

    private Integer budget(String input) {
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("(\\d+)\\s*(?:元|块|以内|预算)").matcher(input);
        Integer latest = null;
        while (matcher.find()) {
            latest = Integer.valueOf(matcher.group(1));
        }
        return latest;
    }

    private Integer numberBefore(String input, String unit) {
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("(\\d+)\\s*" + unit).matcher(input);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private String match(String input, String regex) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(regex).matcher(input);
        return matcher.find() ? matcher.group(1) : null;
    }

    private boolean containsAny(String input, String... values) {
        for (String value : values) {
            if (input.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
