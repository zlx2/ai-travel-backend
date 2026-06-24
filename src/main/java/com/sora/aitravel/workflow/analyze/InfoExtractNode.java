package com.sora.aitravel.workflow.analyze;

import com.sora.aitravel.dto.model.RentalRequirementDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 信息提取节点。
 *
 * <p>实现 Spring AI Alibaba Graph node 接口，是 {@link TripAnalyzeWorkflow} 工作流的第二个步骤。 负责调用 AI 大模型（如
 * DeepSeek）从用户标准化的自然语言输入中提取关键行程字段， 包括出发地、目的地、出行天数、出行时间、偏好等结构化信息。
 *
 * <p>在整个工作流中的位置：流程第 2 步（在输入预处理之后，完整性检查之前）。
 *
 * <p>输入：{@link AnalyzeWorkflowContext#request}（已预处理的用户请求）。 输出：将模型返回的原始 JSON 响应写入 {@link
 * AnalyzeWorkflowContext#rawModelResponse}。
 */
@Component
public class InfoExtractNode {

    private static final List<String> KNOWN_DESTINATIONS =
            List.of(
                    "上海", "重庆", "成都", "都江堰", "杭州", "千岛湖", "黄山", "南京", "西安", "厦门", "云南", "北京", "三亚",
                    "广州", "深圳", "苏州", "无锡", "湖州", "宁波", "长沙", "武汉");

    private static final List<String> KNOWN_PREFERENCES =
            List.of(
                    "美食", "夜景", "历史文化", "自然风光", "亲子", "拍照打卡", "海岛", "轻松游", "自驾", "租车", "周边", "山路",
                    "行李多", "新能源", "商务", "高端", "舒服");

    /**
     * 执行信息提取逻辑——调用 AI 模型从用户输入中提取结构化行程信息。
     *
     * @param context 工作流上下文，读取预处理后的请求并调用模型， 将模型原始响设置到 {@link
     *     AnalyzeWorkflowContext#rawModelResponse}
     */
    public void execute(AnalyzeWorkflowContext context) {
        String input = safeText(context.getRequest().userInput());
        String selectedDestination = safeText(context.getRequest().selectedDestination());

        String departure = extractDeparture(input);
        Integer days = extractInt(input, "(\\d+)\\s*天", null);
        Integer budget = extractInt(input, "预算\\s*(\\d+)", extractInt(input, "(\\d+)\\s*元", null));
        Integer peopleCount = extractInt(input, "(\\d+)\\s*(个人|人)", 2);
        List<String> preferences = extractPreferences(input);
        List<String> mentionedCities = extractCitiesInOrder(input);

        String routeMode = decideRouteMode(input, mentionedCities);
        String routeStructure = decideRouteStructure(input, routeMode);
        String destination =
                decideDestination(selectedDestination, departure, mentionedCities, routeMode);
        List<String> routeCities =
                buildRouteCities(
                        input, departure, destination, mentionedCities, routeMode, routeStructure);
        String routeRegion = decideRouteRegion(input, routeCities);
        String transportMode = decideTransportMode(input, routeMode);
        String rentalIntent = decideRentalIntent(input, routeMode);

        if (preferences.isEmpty()) {
            preferences = List.of("美食", "轻松游");
        }

        RentalRequirementDTO rentalRequirement =
                buildRentalRequirement(
                        input,
                        departure,
                        destination,
                        routeMode,
                        routeStructure,
                        routeCities,
                        preferences,
                        days);

        context.setExtractedRequirement(
                new TravelRequirementDTO(
                        departure,
                        destination,
                        routeMode,
                        routeStructure,
                        routeRegion,
                        routeCities,
                        transportMode,
                        rentalIntent,
                        rentalRequirement,
                        days,
                        budget == null ? 2000 : budget,
                        "TOTAL",
                        peopleCount,
                        preferences,
                        preferences.contains("轻松游") ? "LIGHT" : "NORMAL",
                        List.of(),
                        null));
        context.setRawModelResponse("MOCK_ANALYZE_EXTRACTED");
    }

    private String extractDeparture(String input) {
        Matcher matcher =
                Pattern.compile("从([\\u4e00-\\u9fa5]{2,8}?)(?:出发|去|到|飞|租车|开|自驾|，|,|\\s)")
                        .matcher(input);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private Integer extractInt(String input, String regex, Integer defaultValue) {
        Matcher matcher = Pattern.compile(regex).matcher(input);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return defaultValue;
    }

    private List<String> extractPreferences(String input) {
        List<String> result = new ArrayList<>();
        for (String preference : KNOWN_PREFERENCES) {
            if (input.contains(preference)) {
                result.add(preference);
            }
        }
        return result;
    }

    private List<String> extractCitiesInOrder(String input) {
        List<CityHit> hits = new ArrayList<>();
        for (String city : KNOWN_DESTINATIONS) {
            int index = input.indexOf(city);
            if (index >= 0) {
                hits.add(new CityHit(city, index));
            }
        }
        hits.sort((a, b) -> Integer.compare(a.index(), b.index()));
        Set<String> result = new LinkedHashSet<>();
        for (CityHit hit : hits) {
            result.add(hit.city());
        }
        return new ArrayList<>(result);
    }

    private String decideRouteMode(String input, List<String> cities) {
        boolean rental = containsAny(input, "租车", "取车", "还车");
        boolean drive = containsAny(input, "自驾", "开车", "开到");
        boolean landing = containsAny(input, "飞", "飞机", "下飞机", "落地", "机场", "高铁");
        if (rental && landing) {
            return "LANDING_RENTAL_TRIP";
        }
        if ((rental || drive) && (cities.size() >= 3 || containsAny(input, "一圈", "环线", "多城市"))) {
            return "ROAD_TRIP";
        }
        if (drive && containsAny(input, "开到", "自驾到") && cities.size() >= 2) {
            return "ROAD_TRIP";
        }
        return "DESTINATION_CITY_TRIP";
    }

    private String decideRouteStructure(String input, String routeMode) {
        if (!"ROAD_TRIP".equals(routeMode)) {
            return "LANDING_RENTAL_TRIP".equals(routeMode) ? "ROUND_TRIP" : "SINGLE_CITY";
        }
        if (containsAny(input, "一圈", "环线", "回到", "返回", "往返")) {
            return "LOOP";
        }
        if (containsAny(input, "开到", "自驾到", "到西安", "到南京")) {
            return "LINEAR";
        }
        return "ROUND_TRIP";
    }

    private String decideDestination(
            String selectedDestination, String departure, List<String> cities, String routeMode) {
        if (!selectedDestination.isBlank()) {
            return selectedDestination;
        }
        for (String city : cities) {
            if (!city.equals(departure)) {
                return city;
            }
        }
        return "ROAD_TRIP".equals(routeMode) ? "" : "";
    }

    private List<String> buildRouteCities(
            String input,
            String departure,
            String destination,
            List<String> cities,
            String routeMode,
            String routeStructure) {
        if (!"ROAD_TRIP".equals(routeMode)) {
            return destination.isBlank() ? List.of() : List.of(destination);
        }
        List<String> route = new ArrayList<>();
        if (!departure.isBlank()) {
            route.add(departure);
        }
        for (String city : cities) {
            if (!route.contains(city)) {
                route.add(city);
            }
        }
        if (route.isEmpty() && !destination.isBlank()) {
            route.add(destination);
        }
        if ("LOOP".equals(routeStructure)
                && !departure.isBlank()
                && !route.get(route.size() - 1).equals(departure)) {
            route.add(departure);
        }
        return route;
    }

    private String decideRouteRegion(String input, List<String> routeCities) {
        if (containsAny(input, "江浙沪", "长三角")) {
            return "江浙沪";
        }
        if (containsAny(input, "川西")) {
            return "川西";
        }
        if (routeCities.size() >= 3) {
            return String.join("-", routeCities);
        }
        return null;
    }

    private String decideTransportMode(String input, String routeMode) {
        if ("ROAD_TRIP".equals(routeMode)) {
            return "SELF_DRIVE";
        }
        if ("LANDING_RENTAL_TRIP".equals(routeMode)) {
            return "RENTAL_CAR";
        }
        if (containsAny(input, "租车")) {
            return "RENTAL_CAR";
        }
        if (containsAny(input, "打车", "出租车")) {
            return "TAXI";
        }
        return "PUBLIC_TRANSIT";
    }

    private String decideRentalIntent(String input, String routeMode) {
        if ("ROAD_TRIP".equals(routeMode) || "LANDING_RENTAL_TRIP".equals(routeMode)) {
            return "USER_REQUIRED";
        }
        if (containsAny(input, "租车", "取车")) {
            return "USER_REQUIRED";
        }
        if (containsAny(input, "要不要租车", "是否租车")) {
            return "USER_UNSURE";
        }
        return "NO_RENTAL";
    }

    private RentalRequirementDTO buildRentalRequirement(
            String input,
            String departure,
            String destination,
            String routeMode,
            String routeStructure,
            List<String> routeCities,
            List<String> preferences,
            Integer days) {
        boolean needRental =
                !"DESTINATION_CITY_TRIP".equals(routeMode) || containsAny(input, "租车", "自驾");
        String rentalStartCity = null;
        String rentalEndCity = null;
        if ("ROAD_TRIP".equals(routeMode)) {
            rentalStartCity = departure;
            rentalEndCity =
                    Boolean.TRUE.equals(isOneWay(routeStructure))
                            ? (routeCities.isEmpty()
                                    ? destination
                                    : routeCities.get(routeCities.size() - 1))
                            : departure;
        } else if ("LANDING_RENTAL_TRIP".equals(routeMode) || needRental) {
            rentalStartCity = destination;
            rentalEndCity = destination;
        }
        String pickupMode = decidePickupMode(input);
        boolean delivery = "DELIVERY".equals(pickupMode);
        return new RentalRequirementDTO(
                needRental,
                rentalStartCity,
                rentalEndCity,
                pickupMode,
                delivery ? "DELIVERY" : pickupMode,
                rentalStartCity,
                rentalEndCity,
                decideVehiclePreference(input, preferences),
                days,
                delivery,
                delivery ? extractAddress(input, "送到") : null,
                delivery ? extractAddress(input, "上门还") : null,
                isOneWay(routeStructure));
    }

    private String decidePickupMode(String input) {
        if (containsAny(input, "送车上门", "送到")) {
            return "DELIVERY";
        }
        if (containsAny(input, "下飞机", "机场", "飞机", "飞")) {
            return "AIRPORT";
        }
        if (containsAny(input, "高铁", "火车站", "车站")) {
            return "TRAIN_STATION";
        }
        if (containsAny(input, "市区", "门店", "POI")) {
            return "CITY_POI";
        }
        return "UNKNOWN";
    }

    private String decideVehiclePreference(String input, List<String> preferences) {
        String text = input + String.join(",", preferences);
        if (containsAny(text, "新能源", "电车")) {
            return "EV_SEDAN";
        }
        if (containsAny(text, "商务", "高端", "豪华", "预算高")) {
            return "LUXURY_SEDAN";
        }
        if (containsAny(text, "5人", "6人", "7人", "五人", "六人", "七人")) {
            return "MPV";
        }
        if (containsAny(text, "SUV", "山路", "自然风光", "亲子", "行李多")) {
            return "SUV";
        }
        if (containsAny(text, "舒服", "舒适")) {
            return "COMFORT_SEDAN";
        }
        return "ECONOMY_SEDAN";
    }

    private Boolean isOneWay(String routeStructure) {
        return "LINEAR".equals(routeStructure);
    }

    private String extractAddress(String input, String marker) {
        int index = input.indexOf(marker);
        if (index < 0) {
            return null;
        }
        String value = input.substring(index + marker.length()).trim();
        return value.length() > 40 ? value.substring(0, 40) : value;
    }

    private boolean containsAny(String input, String... values) {
        for (String value : values) {
            if (input.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private record CityHit(String city, int index) {}
}
