package com.sora.aitravel.service.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sora.aitravel.common.enums.FoodSearchIntentTypeEnum;
import com.sora.aitravel.config.AmapProperties;
import com.sora.aitravel.dto.model.FoodRestaurantItemDTO;
import com.sora.aitravel.dto.response.FoodRecommendResponse;
import com.sora.aitravel.service.FoodRecommendService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 美食饭店推荐业务实现。
 *
 * <p>整体流程可以按 4 步理解：
 *
 * <ol>
 *   <li>解析用户 query，判断查当前位置附近、地点附近，还是城市关键词；
 *   <li>根据意图调用高德 Web Service；
 *   <li>把高德 POI 字段转换成 FoodRestaurantItemDTO；
 *   <li>基于真实字段生成推荐理由，LLM 失败时用模板兜底。
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FoodRecommendServiceImpl implements FoodRecommendService {

    /** 高德餐饮服务大类编码。050000 表示餐饮服务。 */
    private static final String AMAP_FOOD_TYPE = "050000";

    /** 请求 business 扩展字段，里面可能包含评分、人均、标签、营业时间等信息。 */
    private static final String AMAP_BUSINESS_FIELDS = "business";

    /** 默认搜索半径，单位：米。 */
    private static final int DEFAULT_RADIUS = 1500;

    /** 默认每页返回饭店数量。 */
    private static final int DEFAULT_PAGE_SIZE = 10;

    /** 高德 page_size 最大限制为 25，这里做一层保护。 */
    private static final int MAX_PAGE_SIZE = 25;

    /** 出现这些词时，优先理解为“查当前位置附近”。 */
    private static final List<String> NEAR_WORDS = List.of("附近", "周边", "我身边", "当前位置", "就近");

    /** 出现这些词且前面有地点时，理解为“查某个地点附近”。 */
    private static final List<String> ADDRESS_NEAR_WORDS = List.of("附近", "周边", "旁边");

    /** 规则能直接识别的常见城市。不在列表里的城市，会交给 LLM 尝试解析。 */
    private static final List<String> KNOWN_CITIES =
            List.of(
                    "北京", "上海", "广州", "深圳", "重庆", "成都", "西安", "杭州", "武汉", "南京",
                    "厦门", "三亚", "长沙", "苏州", "天津", "青岛", "大理", "丽江", "昆明");

    /** 高德配置，读取 app.amap.api-key/base-url/timeout。 */
    private final AmapProperties amapProperties;

    /** 用于把 LLM 返回的 JSON 字符串转成 Java 对象。 */
    private final ObjectMapper objectMapper;

    /** 用于兜底解析 query 和生成推荐理由。 */
    private final ChatModel chatModel;

    @Override
    public FoodRecommendResponse recommend(
            String query, String currentLocation, Integer radius, Integer pageSize, Integer pageNum) {
        if (!StringUtils.hasText(query)) {
            return FoodRecommendResponse.fail("请输入想查询的美食内容");
        }
        if (missingAmapApiKey()) {
            return FoodRecommendResponse.fail("高德 API Key 未配置，请先设置 AMAP_API_KEY");
        }

        FoodSearchIntent intent = resolveIntent(query);
        if (intent == null || intent.getIntentType() == null) {
            return FoodRecommendResponse.fail("请说明想查哪个城市、哪个地点附近，或允许获取当前位置");
        }

        try {
            return executeSearch(
                    query,
                    intent,
                    currentLocation,
                    normalizeRadius(radius),
                    normalizePageSize(pageSize),
                    normalizePageNum(pageNum));
        } catch (RuntimeException exception) {
            log.warn("Food recommendation failed, query={}", query, exception);
            return FoodRecommendResponse.fail("美食推荐查询失败：" + exception.getMessage());
        }
    }

    /**
     * 根据意图选择对应的高德查询方式。
     *
     * <p>NEAR_CURRENT 和 NEAR_ADDRESS 都走周边搜索，CITY_KEYWORD 走关键字搜索。
     */
    private FoodRecommendResponse executeSearch(
            String query,
            FoodSearchIntent intent,
            String currentLocation,
            Integer radius,
            Integer pageSize,
            Integer pageNum) {
        if (intent.getIntentType() == FoodSearchIntentTypeEnum.NEAR_CURRENT) {
            if (!StringUtils.hasText(currentLocation)) {
                return FoodRecommendResponse.fail("请先允许获取当前位置，或输入具体地点，例如：洪崖洞附近火锅");
            }
            JSONObject json =
                    searchAround(currentLocation, intent.getKeywords(), intent.getCity(), radius, pageSize, pageNum);
            return buildResponse(query, intent, "AROUND", currentLocation, "当前位置", json);
        }

        if (intent.getIntentType() == FoodSearchIntentTypeEnum.NEAR_ADDRESS) {
            if (!StringUtils.hasText(intent.getAddress())) {
                return FoodRecommendResponse.fail("请补充具体地点，例如：洪崖洞附近火锅");
            }
            String centerLocation = geocodeToLocation(intent.getAddress(), intent.getCity());
            JSONObject json =
                    searchAround(centerLocation, intent.getKeywords(), intent.getCity(), radius, pageSize, pageNum);
            return buildResponse(query, intent, "AROUND", centerLocation, "搜索地点", json);
        }

        if (intent.getIntentType() == FoodSearchIntentTypeEnum.CITY_KEYWORD) {
            if (!StringUtils.hasText(intent.getCity()) || !StringUtils.hasText(intent.getKeywords())) {
                return FoodRecommendResponse.fail("请补充城市和美食关键词，例如：重庆火锅");
            }
            JSONObject json = searchText(intent.getCity(), intent.getKeywords(), pageSize, pageNum);
            return buildResponse(query, intent, "TEXT", null, "搜索地点", json);
        }

        return FoodRecommendResponse.fail("暂不支持该美食查询意图");
    }

    /** 把高德原始 JSON 结果转换成工具统一返回对象。 */
    private FoodRecommendResponse buildResponse(
            String query,
            FoodSearchIntent intent,
            String queryType,
            String centerLocation,
            String distanceTargetText,
            JSONObject amapResponse) {
        if (!isAmapSuccess(amapResponse)) {
            return FoodRecommendResponse.fail("高德 API 调用失败：" + text(amapResponse, "info"));
        }

        JSONArray pois = amapResponse.getJSONArray("pois");
        if (pois == null || pois.isEmpty()) {
            return new FoodRecommendResponse(
                    true, "未找到符合条件的饭店", "AMAP", queryType, intent.getIntentType(), centerLocation, 0, List.of());
        }

        List<FoodRestaurantItemDTO> items = new ArrayList<>();
        for (int i = 0; i < pois.size(); i++) {
            JSONObject poi = pois.getJSONObject(i);
            if (isValidFoodPoi(poi)) {
                items.add(toFoodItem(poi, distanceTargetText));
            }
        }

        List<String> reasons = generateReasons(query, items);
        List<FoodRestaurantItemDTO> withReasons = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            String reason = i < reasons.size() ? reasons.get(i) : templateReason(items.get(i));
            withReasons.add(withReason(items.get(i), reason));
        }

        return new FoodRecommendResponse(
                true,
                "success",
                "AMAP",
                queryType,
                intent.getIntentType(),
                centerLocation,
                withReasons.size(),
                withReasons);
    }

    /**
     * 解析用户查询意图。
     *
     * <p>先走规则，因为规则更稳定、速度更快；规则判断不出来时，再交给 LLM 兜底。
     */
    private FoodSearchIntent resolveIntent(String query) {
        FoodSearchIntent ruleIntent = parseIntentByRule(query);
        return ruleIntent != null ? ruleIntent : parseIntentByLlm(query);
    }

    /** 用规则解析常见表达。 */
    private FoodSearchIntent parseIntentByRule(String query) {
        if (!StringUtils.hasText(query)) {
            return null;
        }

        /*
         * 正则说明：
         * \\s+ 表示“一个或多个空白字符”，包括空格、Tab、换行。
         * 这里把空白全部去掉，是为了让“洪崖洞 附近 火锅”和“洪崖洞附近火锅”按同一种方式处理。
         */
        String normalized = query.replaceAll("\\s+", "").trim();

        FoodSearchIntent nearCurrent = parseNearCurrent(normalized);
        if (nearCurrent != null) {
            return nearCurrent;
        }

        FoodSearchIntent nearAddress = parseNearAddress(normalized);
        if (nearAddress != null) {
            return nearAddress;
        }

        return parseCityKeyword(normalized);
    }

    /** 解析“附近有什么好吃的”“我身边火锅”这类当前位置附近查询。 */
    private FoodSearchIntent parseNearCurrent(String query) {
        for (String word : NEAR_WORDS) {
            if (query.startsWith(word) || query.contains("我" + word)) {
                String keywords = cleanupKeywords(query.replace(word, ""));
                return new FoodSearchIntent(
                        FoodSearchIntentTypeEnum.NEAR_CURRENT, null, null, defaultKeyword(keywords));
            }
        }
        return null;
    }

    /** 解析“洪崖洞附近火锅”“解放碑周边小吃”这类地点附近查询。 */
    private FoodSearchIntent parseNearAddress(String query) {
        for (String word : ADDRESS_NEAR_WORDS) {
            int index = query.indexOf(word);
            if (index <= 0) {
                continue;
            }

            String address = query.substring(0, index);
            if (address.contains("我") || address.contains("当前位置") || address.contains("身边")) {
                continue;
            }

            String city = extractCity(query);
            String keywords = cleanupKeywords(query.substring(index + word.length()));
            if (StringUtils.hasText(city)) {
                address = address.replace(city, "");
            }

            return new FoodSearchIntent(
                    FoodSearchIntentTypeEnum.NEAR_ADDRESS, city, address, defaultKeyword(keywords));
        }
        return null;
    }

    /** 解析“重庆火锅推荐”“成都串串推荐”这类城市关键词查询。 */
    private FoodSearchIntent parseCityKeyword(String query) {
        String city = extractCity(query);
        if (!StringUtils.hasText(city)) {
            return null;
        }

        String keywords = cleanupKeywords(query.replaceFirst(city, ""));
        return new FoodSearchIntent(FoodSearchIntentTypeEnum.CITY_KEYWORD, city, null, defaultKeyword(keywords));
    }

    /** 规则解析失败时，让 LLM 尝试抽取 intentType/city/address/keywords。 */
    private FoodSearchIntent parseIntentByLlm(String query) {
        try {
            String content =
                    ChatClient.builder(chatModel)
                            .build()
                            .prompt()
                            .system(
                                    """
                                    你负责把用户美食查询解析成 JSON。
                                    intentType 只能是 NEAR_CURRENT、NEAR_ADDRESS、CITY_KEYWORD。
                                    只返回 JSON，不要 Markdown。
                                    字段：intentType, city, address, keywords。
                                    """)
                            .user("用户输入：" + query)
                            .call()
                            .content();
            LlmIntentResult result = objectMapper.readValue(content, LlmIntentResult.class);
            FoodSearchIntentTypeEnum type = FoodSearchIntentTypeEnum.valueOf(result.getIntentType());
            return new FoodSearchIntent(type, result.getCity(), result.getAddress(), defaultKeyword(result.getKeywords()));
        } catch (IllegalArgumentException | JsonProcessingException exception) {
            log.warn("Food intent LLM parse failed, query={}", query, exception);
            return null;
        }
    }

    /** 高德 POI 转成饭店推荐 DTO。 */
    private FoodRestaurantItemDTO toFoodItem(JSONObject poi, String distanceTargetText) {
        String location = text(poi, "location");
        BigDecimal longitude = null;
        BigDecimal latitude = null;
        if (StringUtils.hasText(location) && location.contains(",")) {
            String[] parts = location.split(",");
            longitude = parseDecimal(parts[0]);
            latitude = parts.length > 1 ? parseDecimal(parts[1]) : null;
        }

        JSONObject business = poi.getJSONObject("business");
        String rating = valueFromBusinessOrPoi(business, poi, "rating", "biz_ext_rating");
        String avgCost = valueFromBusinessOrPoi(business, poi, "cost", "biz_ext_cost");
        String tag = valueFromBusinessOrPoi(business, poi, "tag", null);
        String keyTag = valueFromBusinessOrPoi(business, poi, "keytag", null);
        String recTag = valueFromBusinessOrPoi(business, poi, "rectag", null);
        String openTime =
                firstText(
                        valueFromBusinessOrPoi(business, poi, "opentime_today", null),
                        valueFromBusinessOrPoi(business, poi, "opentime_week", null));
        String businessArea = valueFromBusinessOrPoi(business, poi, "business_area", null);
        String tel = valueFromBusinessOrPoi(business, poi, "tel", "tel");
        Integer distance = parseInteger(text(poi, "distance"));

        return new FoodRestaurantItemDTO(
                text(poi, "id"),
                text(poi, "name"),
                text(poi, "address"),
                text(poi, "cityname"),
                text(poi, "adname"),
                text(poi, "adcode"),
                text(poi, "type"),
                text(poi, "typecode"),
                extractFoodType(keyTag, recTag, tag, text(poi, "type")),
                location,
                longitude,
                latitude,
                distance,
                distanceText(distance, distanceTargetText),
                rating,
                avgCost,
                tag,
                keyTag,
                recTag,
                openTime,
                businessArea,
                tel,
                null,
                "");
    }

    /** 判断高德 POI 是否真的是餐饮类结果。 */
    private boolean isValidFoodPoi(JSONObject poi) {
        if (!StringUtils.hasText(text(poi, "name")) || !StringUtils.hasText(text(poi, "location"))) {
            return false;
        }
        String type = text(poi, "type");
        String typeCode = text(poi, "typecode");
        return (StringUtils.hasText(type) && type.contains("餐饮服务"))
                || (StringUtils.hasText(typeCode) && typeCode.startsWith("05"));
    }

    /** 给 DTO 补上推荐理由。 */
    private FoodRestaurantItemDTO withReason(FoodRestaurantItemDTO item, String reason) {
        return new FoodRestaurantItemDTO(
                item.getAmapPoiId(),
                item.getName(),
                item.getAddress(),
                item.getCityName(),
                item.getAdName(),
                item.getAdCode(),
                item.getType(),
                item.getTypeCode(),
                item.getFoodType(),
                item.getLocation(),
                item.getLongitude(),
                item.getLatitude(),
                item.getDistance(),
                item.getDistanceText(),
                item.getRating(),
                item.getAvgCost(),
                item.getTag(),
                item.getKeyTag(),
                item.getRecTag(),
                item.getOpenTime(),
                item.getBusinessArea(),
                item.getTel(),
                reason,
                item.getNavigationUrl());
    }

    /**
     * 生成推荐理由。
     *
     * <p>优先使用 LLM，让文案更自然；如果 LLM 调用失败或返回数量不对，就使用模板理由兜底。
     */
    private List<String> generateReasons(String query, List<FoodRestaurantItemDTO> items) {
        if (items.isEmpty()) {
            return List.of();
        }
        try {
            List<String> llmReasons = generateReasonsByLlm(query, items);
            if (llmReasons.size() == items.size()) {
                return llmReasons;
            }
        } catch (RuntimeException exception) {
            log.warn("Food reason LLM generation failed, fallback to template", exception);
        }
        return items.stream().map(this::templateReason).toList();
    }

    /** 调用 LLM 批量生成推荐理由。 */
    private List<String> generateReasonsByLlm(String query, List<FoodRestaurantItemDTO> items) {
        String reply =
                ChatClient.builder(chatModel)
                        .build()
                        .prompt()
                        .system(
                                """
                                你是旅游美食推荐助手。请只基于用户 query 和高德 POI 真实字段生成推荐理由。
                                禁止编造评分、人均、排队时间、评论、营业时间、招牌菜排名。
                                每家店输出一行，格式为：序号. 推荐理由
                                每条 50 字以内，语气自然，适合前端卡片展示。
                                """)
                        .user(buildReasonPrompt(query, items))
                        .call()
                        .content();
        return parseNumberedLines(reply, items.size());
    }

    /** 组装给 LLM 的饭店字段，只放高德真实返回字段。 */
    private String buildReasonPrompt(String query, List<FoodRestaurantItemDTO> items) {
        StringBuilder builder = new StringBuilder();
        builder.append("用户 query：").append(query).append("\n\n");
        for (int i = 0; i < items.size(); i++) {
            FoodRestaurantItemDTO item = items.get(i);
            builder.append(i + 1)
                    .append(". name=")
                    .append(item.getName())
                    .append(", address=")
                    .append(item.getAddress())
                    .append(", type=")
                    .append(item.getType())
                    .append(", distance=")
                    .append(item.getDistanceText())
                    .append(", rating=")
                    .append(item.getRating())
                    .append(", avgCost=")
                    .append(item.getAvgCost())
                    .append(", tag=")
                    .append(item.getTag())
                    .append(", keyTag=")
                    .append(item.getKeyTag())
                    .append(", recTag=")
                    .append(item.getRecTag())
                    .append(", openTime=")
                    .append(item.getOpenTime())
                    .append("\n");
        }
        return builder.toString();
    }

    /** 从 LLM 返回的“1. xxx”这种多行文本中提取推荐理由。 */
    private List<String> parseNumberedLines(String reply, int expectedSize) {
        List<String> reasons = new ArrayList<>();
        if (!StringUtils.hasText(reply)) {
            return reasons;
        }
        for (String line : reply.split("\\R")) {
            /*
             * 正则说明：
             * ^\\s*       匹配行首可能存在的空格。
             * \\d+        匹配序号数字，例如 1、2、10。
             * [\\.、)]    匹配序号后面的分隔符，例如 "."、"、"、")"。
             * \\s*        匹配分隔符后可能存在的空格。
             *
             * 作用：把 "1. 推荐理由" 清理成 "推荐理由"。
             */
            String cleaned = line.replaceFirst("^\\s*\\d+[\\.、)]\\s*", "").trim();
            if (StringUtils.hasText(cleaned)) {
                reasons.add(cleaned);
            }
            if (reasons.size() == expectedSize) {
                break;
            }
        }
        return reasons;
    }

    /** LLM 不可用时的模板理由，只拼接高德真实字段。 */
    private String templateReason(FoodRestaurantItemDTO item) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(item.getDistanceText())) {
            parts.add(item.getDistanceText());
        }
        if (StringUtils.hasText(item.getFoodType())) {
            parts.add("主打" + item.getFoodType());
        } else if (StringUtils.hasText(item.getType())) {
            parts.add("属于" + item.getType());
        }
        if (StringUtils.hasText(item.getRating())) {
            parts.add("高德返回评分" + item.getRating());
        }
        if (StringUtils.hasText(item.getAvgCost())) {
            parts.add("人均约" + item.getAvgCost() + "元");
        }
        if (parts.isEmpty()) {
            return "这家店有高德 POI 数据返回，可作为本次美食查询的候选饭店。";
        }
        return String.join("，", parts) + "，适合作为本次美食查询的候选。";
    }

    /** 调用高德地理编码，把文字地点转成经纬度。 */
    private String geocodeToLocation(String address, String city) {
        JSONObject json = geocode(address, city);
        if (!isAmapSuccess(json)) {
            throw new IllegalStateException("地理编码失败：" + text(json, "info"));
        }
        JSONArray geocodes = json.getJSONArray("geocodes");
        if (geocodes == null || geocodes.isEmpty()) {
            throw new IllegalStateException("未解析到地点坐标");
        }
        String location = text(geocodes.getJSONObject(0), "location");
        if (!StringUtils.hasText(location)) {
            throw new IllegalStateException("地点坐标为空");
        }
        return location;
    }

    /** 判断是否缺少高德 Key。 */
    private boolean missingAmapApiKey() {
        return amapProperties.getApiKey() == null || amapProperties.getApiKey().isBlank();
    }

    /** 调用高德周边搜索：用于当前位置附近、景点附近这两类查询。 */
    private JSONObject searchAround(
            String location,
            String keywords,
            String region,
            Integer radius,
            Integer pageSize,
            Integer pageNum) {
        HttpRequest request =
                amapGet("/v5/place/around")
                        .form("location", location)
                        .form("types", AMAP_FOOD_TYPE)
                        .form("radius", radius)
                        .form("sortrule", "distance")
                        .form("show_fields", AMAP_BUSINESS_FIELDS)
                        .form("page_size", pageSize)
                        .form("page_num", pageNum);
        if (StringUtils.hasText(keywords)) {
            request.form("keywords", keywords);
        }
        if (StringUtils.hasText(region)) {
            request.form("region", region).form("city_limit", "true");
        }
        return executeJson(request);
    }

    /** 调用高德关键字搜索：用于“重庆火锅推荐”这类城市美食查询。 */
    private JSONObject searchText(String city, String keywords, Integer pageSize, Integer pageNum) {
        return executeJson(
                amapGet("/v5/place/text")
                        .form("keywords", keywords)
                        .form("region", city)
                        .form("city_limit", "true")
                        .form("types", AMAP_FOOD_TYPE)
                        .form("show_fields", AMAP_BUSINESS_FIELDS)
                        .form("page_size", pageSize)
                        .form("page_num", pageNum));
    }

    /** 调用高德地理编码接口。 */
    private JSONObject geocode(String address, String city) {
        HttpRequest request = amapGet("/v3/geocode/geo").form("address", address);
        if (StringUtils.hasText(city)) {
            request.form("city", city);
        }
        return executeJson(request);
    }

    /** 构造高德 GET 请求，统一拼接 baseUrl、key 和 timeout。 */
    private HttpRequest amapGet(String path) {
        String baseUrl = amapProperties.getBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            baseUrl = "https://restapi.amap.com";
        }
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        int timeoutMillis =
                amapProperties.getTimeout() == null
                        ? 10000
                        : Math.toIntExact(amapProperties.getTimeout().toMillis());
        return HttpRequest.get(baseUrl + path)
                .timeout(timeoutMillis)
                .form("key", amapProperties.getApiKey());
    }

    /** 执行 HTTP 请求，并把响应体解析成 Hutool JSONObject。 */
    private JSONObject executeJson(HttpRequest request) {
        return JSONUtil.parseObj(request.execute().body());
    }

    /** 判断高德接口是否成功。status=1 且 infocode=10000 表示成功。 */
    private boolean isAmapSuccess(JSONObject json) {
        return json != null && "1".equals(text(json, "status")) && "10000".equals(text(json, "infocode"));
    }

    /** 半径为空或小于等于 0 时使用默认值。 */
    private Integer normalizeRadius(Integer radius) {
        return radius == null || radius <= 0 ? DEFAULT_RADIUS : radius;
    }

    /** 每页数量为空时使用默认值，超过高德上限时截断。 */
    private Integer normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    /** 页码为空或小于等于 0 时使用第一页。 */
    private Integer normalizePageNum(Integer pageNum) {
        return pageNum == null || pageNum <= 0 ? 1 : pageNum;
    }

    /** 从 query 中提取已知城市。 */
    private String extractCity(String query) {
        for (String city : KNOWN_CITIES) {
            if (query.contains(city)) {
                return city;
            }
        }
        return null;
    }

    /** 清理 query 中无实际搜索意义的口语词，只留下真正要搜的美食关键词。 */
    private String cleanupKeywords(String value) {
        if (value == null) {
            return null;
        }

        /*
         * 正则说明：
         * (A|B|C) 表示匹配 A、B、C 中任意一个词。
         * 这里把“推荐、有什么、好吃的、饭店、餐厅”等口语词替换为空，
         * 例如“重庆火锅推荐”会变成“重庆火锅”，“附近有什么好吃的”会尽量变成空，后面再默认成“美食”。
         */
        String cleaned =
                value.replaceAll("(推荐|有什么|好吃的|吃的|饭店|餐厅|美食|地方|帮我|给我|找|查|的)", "");
        return cleaned.trim();
    }

    /** 没有明确关键词时，默认查“美食”。 */
    private String defaultKeyword(String keywords) {
        return StringUtils.hasText(keywords) ? keywords : "美食";
    }

    /** 优先从 business 扩展字段取值，取不到再从 POI 根字段取值。 */
    private String valueFromBusinessOrPoi(
            JSONObject business, JSONObject poi, String businessKey, String poiKey) {
        if (business != null && StringUtils.hasText(text(business, businessKey))) {
            return text(business, businessKey);
        }
        return StringUtils.hasText(poiKey) ? text(poi, poiKey) : null;
    }

    /** 从多个标签字段中提取一个较短的美食类型。 */
    private String extractFoodType(String keyTag, String recTag, String tag, String type) {
        String source = firstText(keyTag, recTag, tag, type);
        if (!StringUtils.hasText(source)) {
            return null;
        }

        /*
         * 正则说明：
         * [;,；、] 表示按英文分号、英文逗号、中文分号、顿号切分。
         * 高德类型可能是“餐饮服务;中餐厅;火锅店”，切开后取第一个短标签用于展示。
         */
        String[] parts = source.split("[;,；、]");
        return parts.length == 0 ? source : parts[0];
    }

    /** 把距离米数转成用户可读文案。 */
    private String distanceText(Integer distance, String targetText) {
        if (distance == null) {
            return null;
        }
        if (distance >= 1000) {
            return "距" + targetText + "约" + String.format("%.1f", distance / 1000.0) + "公里";
        }
        return "距" + targetText + "约" + distance + "米";
    }

    /** 返回第一个非空字符串。 */
    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    /** 安全转换 BigDecimal，转换失败时返回 null。 */
    private BigDecimal parseDecimal(String value) {
        try {
            return StringUtils.hasText(value) ? new BigDecimal(value.trim()) : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /** 安全转换 Integer，转换失败时返回 null。 */
    private Integer parseInteger(String value) {
        try {
            return StringUtils.hasText(value) ? Integer.valueOf(value.trim()) : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /** 从 Hutool JSONObject 中安全取字符串，避免 null 和空数组影响判断。 */
    private String text(JSONObject object, String fieldName) {
        if (object == null) {
            return "";
        }
        Object value = object.get(fieldName);
        if (value == null) {
            return "";
        }
        if (value instanceof JSONArray array && array.isEmpty()) {
            return "";
        }
        return String.valueOf(value);
    }

    /** Service 内部暂存 query 解析结果的意图对象，非 DTO。 */
    @Data
    @AllArgsConstructor
    private static class FoodSearchIntent {
        private FoodSearchIntentTypeEnum intentType;
        private String city;
        private String address;
        private String keywords;
    }

    /** LLM 兜底解析 query 时接收 JSON 的对象，Jackson 需要无参构造。 */
    @Data
    @NoArgsConstructor
    private static class LlmIntentResult {
        private String intentType;
        private String city;
        private String address;
        private String keywords;
    }
}
