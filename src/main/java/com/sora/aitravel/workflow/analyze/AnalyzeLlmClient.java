package com.sora.aitravel.workflow.analyze;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.common.utils.CityNameUtils;
import com.sora.aitravel.config.AiGateway;
import com.sora.aitravel.dto.model.RentalRequirementDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Analyze 工作流里的真实 LLM 调用封装。 */
@Component
@RequiredArgsConstructor
public class AnalyzeLlmClient {

    private static final Set<String> PACES = Set.of("LIGHT", "NORMAL", "TIGHT");
    private static final Set<String> ROUTE_MODES =
            Set.of("DESTINATION_CITY_TRIP", "ROAD_TRIP", "LANDING_RENTAL_TRIP", "REGION_ROUTE");
    private static final Set<String> ROUTE_STRUCTURES =
            Set.of("SINGLE_CITY", "MULTI_CITY", "LOOP", "ONE_WAY");
    private static final Set<String> TRANSPORT_MODES =
            Set.of("PUBLIC_TRANSIT", "SELF_DRIVE", "RENTAL_CAR", "MIXED");
    private static final Set<String> RENTAL_INTENTS =
            Set.of("NO_RENTAL", "USER_REQUIRED", "CONSIDERING");
    private static final Set<String> BUDGET_TYPES = Set.of("TOTAL", "DAILY", "PER_PERSON");

    private final AiGateway aiGateway;
    private final ObjectMapper objectMapper;

    public TravelRequirementDTO extractRequirement(String cleanInput, String selectedDestination) {
        String json =
                aiGateway.callJsonObject(
                        "AI 分析",
                        """
                        你是旅行规划系统的 Analyze 节点，只做“需求抽取”，不要生成行程。
                        请从用户输入中抽取旅行需求，并只返回 JSON 对象，不要 Markdown。

                        重要规则：
                        1. 没有明确提到的字段返回 null 或空数组，不要编造。
                        2. 如果“用户已选择目的地”存在，destination 必须使用该值。
                        3. days 只提取天数；budget 只提取人民币金额；peopleCount 只提取人数。
                        4. 所有枚举字段只能使用给定枚举值；不明确时返回 null。
                        5. 用户明确说租车/自驾时，rentalIntent 返回 USER_REQUIRED，transportMode 优先 SELF_DRIVE 或 RENTAL_CAR。
                        6. 用户明确说不租车/公共交通时，rentalIntent 返回 NO_RENTAL，transportMode 返回 PUBLIC_TRANSIT。
                        7. routeCities 只放明确目的地或途经城市，不要编造城市。
                        8. 城市字段只能放城市名，不能放具体地点。比如“成都双流机场/成都东站/成都春熙路”应抽取 destination=成都、routeCities=["成都"]；具体到达点可放到 rentalRequirement.deliveryAddress。

                        JSON 字段：
                        {
                          "departure": string|null,
                          "destination": string|null,
                          "routeMode": "DESTINATION_CITY_TRIP"|"ROAD_TRIP"|"LANDING_RENTAL_TRIP"|"REGION_ROUTE"|null,
                          "routeStructure": "SINGLE_CITY"|"MULTI_CITY"|"LOOP"|"ONE_WAY"|null,
                          "routeRegion": string|null,
                          "routeCities": string[],
                          "transportMode": "PUBLIC_TRANSIT"|"SELF_DRIVE"|"RENTAL_CAR"|"MIXED"|null,
                          "rentalIntent": "NO_RENTAL"|"USER_REQUIRED"|"CONSIDERING"|null,
                          "rentalRequirement": {
                            "needRental": boolean|null,
                            "rentalStartCity": string|null,
                            "rentalEndCity": string|null,
                            "pickupMode": string|null,
                            "returnMode": string|null,
                            "pickupCity": string|null,
                            "returnCity": string|null,
                            "vehiclePreference": string|null,
                            "rentalDays": number|null,
                            "deliveryRequired": boolean|null,
                            "deliveryAddress": string|null,
                            "returnAddress": string|null,
                            "isOneWay": boolean|null
                          }|null,
                          "days": number|null,
                          "budget": number|null,
                          "budgetType": "TOTAL"|"DAILY"|"PER_PERSON"|null,
                          "peopleCount": number|null,
                          "preferences": string[],
                          "pace": string|null,
                          "avoidances": string[],
                          "travelDate": string|null
                        }

                        用户输入：
                        %s
                        """
                                .formatted(cleanInput));
        JsonNode root = readTree(json);

        String destination =
                CityNameUtils.firstNonBlankCity(selectedDestination, text(root, "destination"));
        List<String> routeCities =
                CityNameUtils.normalizeCityList(stringList(root.get("routeCities")));
        RentalRequirementDTO rentalRequirement = rentalRequirement(root.get("rentalRequirement"));
        standardizeRentalRequirement(rentalRequirement, destination, routeCities);

        return new TravelRequirementDTO(
                text(root, "departure"),
                destination,
                enumValue(root, "routeMode", ROUTE_MODES, null),
                enumValue(root, "routeStructure", ROUTE_STRUCTURES, null),
                text(root, "routeRegion"),
                routeCities,
                enumValue(root, "transportMode", TRANSPORT_MODES, null),
                enumValue(root, "rentalIntent", RENTAL_INTENTS, null),
                rentalRequirement,
                integer(root, "days"),
                integer(root, "budget"),
                enumValue(root, "budgetType", BUDGET_TYPES, null),
                integer(root, "peopleCount"),
                stringList(root.get("preferences")),
                enumValue(root, "pace", PACES, null),
                stringList(root.get("avoidances")),
                text(root, "travelDate"));
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

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_FORMAT_ERROR, "AI 返回 JSON 解析失败");
        }
    }

    private String enumValue(
            JsonNode root, String field, Set<String> allowed, String defaultValue) {
        String value = text(root, field);
        return value != null && allowed.contains(value) ? value : defaultValue;
    }

    private Integer integer(JsonNode root, String field) {
        JsonNode node = root == null ? null : root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asInt();
        }
        String value = node.asText();
        if (!hasText(value)) {
            return null;
        }
        try {
            String normalized = value.toLowerCase();
            if (normalized.matches("\\d+(\\.\\d+)?\\s*k")) {
                return (int) (Double.parseDouble(normalized.replace("k", "").trim()) * 1000);
            }
            Integer chineseNumber = chineseNumber(normalized);
            if (chineseNumber != null) {
                return chineseNumber;
            }
            return Integer.parseInt(normalized.replaceAll("[^0-9-]", ""));
        } catch (Exception ex) {
            return null;
        }
    }

    private Integer chineseNumber(String value) {
        String text = value.replaceAll("[天日元人个,，\\s]", "");
        if (text.isBlank()) {
            return null;
        }
        return switch (text) {
            case "一" -> 1;
            case "二", "两" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            case "十" -> 10;
            default -> null;
        };
    }

    private Boolean bool(JsonNode root, String field) {
        JsonNode node = root == null ? null : root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        String value = node.asText();
        if (!hasText(value)) {
            return null;
        }
        return switch (value.trim().toLowerCase()) {
            case "true", "yes", "y", "1", "需要", "是" -> Boolean.TRUE;
            case "false", "no", "n", "0", "不需要", "否" -> Boolean.FALSE;
            default -> null;
        };
    }

    private RentalRequirementDTO rentalRequirement(JsonNode node) {
        if (node == null || node.isNull() || !node.isObject()) {
            return null;
        }
        return new RentalRequirementDTO(
                bool(node, "needRental"),
                text(node, "rentalStartCity"),
                text(node, "rentalEndCity"),
                text(node, "pickupMode"),
                text(node, "returnMode"),
                text(node, "pickupCity"),
                text(node, "returnCity"),
                text(node, "vehiclePreference"),
                integer(node, "rentalDays"),
                bool(node, "deliveryRequired"),
                text(node, "deliveryAddress"),
                text(node, "returnAddress"),
                bool(node, "isOneWay"));
    }

    private String text(JsonNode root, String field) {
        JsonNode node = root == null ? null : root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return hasText(value) && !"null".equalsIgnoreCase(value) ? value.trim() : null;
    }

    private List<String> stringList(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                String value = item.asText();
                if (hasText(value)) {
                    result.add(value.trim());
                }
            }
            return result;
        }
        String value = node.asText();
        if (hasText(value)) {
            for (String item : value.split("[,，、]")) {
                if (hasText(item)) {
                    result.add(item.trim());
                }
            }
        }
        return result;
    }

    private String firstNonBlank(String first, String second) {
        return hasText(first) ? first.trim() : hasText(second) ? second.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
