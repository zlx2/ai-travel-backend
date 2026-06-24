package com.sora.aitravel.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.sora.aitravel.client.amap.AmapFoodClient;
import com.sora.aitravel.common.enums.FoodSearchIntentTypeEnum;
import com.sora.aitravel.dto.model.FoodRestaurantItemDTO;
import com.sora.aitravel.dto.response.FoodRecommendResponse;
import com.sora.aitravel.service.FoodRecommendService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 美食饭店推荐业务实现。
 *
 * <p>负责解析用户美食查询、调用高德地图、转换餐饮 POI，并基于高德真实字段生成模板推荐理由。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FoodRecommendServiceImpl implements FoodRecommendService {

    // ==================== 常量区 ====================

    /** 默认搜索半径，单位：米。 */
    private static final int DEFAULT_RADIUS = 1500;

    /** 默认每页返回饭店数量。 */
    private static final int DEFAULT_PAGE_SIZE = 10;

    /** 高德 page_size 最大限制为 25。 */
    private static final int MAX_PAGE_SIZE = 25;

    /** 出现这些词且前面有地点时，理解为“查某个地点附近”。 */
    private static final List<String> ADDRESS_NEAR_WORDS = List.of("附近", "周边", "旁边");

    /** 规则能直接识别的常见城市。 */
    private static final List<String> KNOWN_CITIES =
            List.of(
                    "北京", "上海", "广州", "深圳", "重庆", "成都", "西安", "杭州", "武汉", "南京",
                    "厦门", "三亚", "长沙", "苏州", "天津", "青岛", "大理", "丽江", "昆明");

    // ==================== 依赖注入区 ====================

    /** 美食模块专用高德客户端。 */
    private final AmapFoodClient amapFoodClient;

    // ==================== recommend 主入口 ====================

    @Override
    public FoodRecommendResponse recommend(
            String query, String currentLocation, Integer radius, Integer pageSize, Integer pageNum) {
        if (!StringUtils.hasText(query)) {
            return FoodRecommendResponse.fail("请输入想查询的美食内容");
        }
        if (amapFoodClient.missingApiKey()) {
            return FoodRecommendResponse.fail("高德 API Key 未配置，请先设置 AMAP_API_KEY");
        }

        FoodSearchIntent intent = resolveIntent(query);
        if (intent == null || intent.intentType() == null) {
            return FoodRecommendResponse.fail("请说明想查哪个城市、哪个地点附近，或允许获取当前位置");
        }

        try {
            SearchResult searchResult =
                    executeSearch(
                            intent,
                            currentLocation,
                            normalizeRadius(radius),
                            normalizePageSize(pageSize),
                            normalizePageNum(pageNum));
            return buildResponse(intent, searchResult);
        } catch (IllegalArgumentException exception) {
            return FoodRecommendResponse.fail(exception.getMessage());
        } catch (RuntimeException exception) {
            log.warn("Food recommendation failed, query={}", query, exception);
            return FoodRecommendResponse.fail("美食推荐查询失败：" + exception.getMessage());
        }
    }

    // ==================== 参数处理区 ====================

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

    // ==================== 用户意图解析区 ====================

    /**
     * 解析用户查询意图。
     *
     * <p>第一版只使用规则解析，保证结果稳定且不依赖 LLM。
     */
    private FoodSearchIntent resolveIntent(String query) {
        return parseIntentByRule(query);
    }

    /** 用规则解析常见表达。 */
    private FoodSearchIntent parseIntentByRule(String query) {
        if (!StringUtils.hasText(query)) {
            return null;
        }

        /*
         * 删除所有空白字符，让“洪崖洞 附近 火锅”和“洪崖洞附近火锅”按相同方式处理。
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
        if (!isNearCurrentQuery(query)) {
            return null;
        }

        String keywords = query;
        keywords = keywords.replace("当前位置", "");
        keywords = keywords.replace("我附近", "");
        keywords = keywords.replace("我身边", "");
        keywords = keywords.replace("附近", "");
        keywords = keywords.replace("周边", "");
        keywords = keywords.replace("身边", "");
        keywords = keywords.replace("就近", "");
        keywords = cleanupKeywords(keywords);

        return new FoodSearchIntent(
                FoodSearchIntentTypeEnum.NEAR_CURRENT, null, null, defaultKeyword(keywords));
    }

    /** 判断 query 是否明确表示查询用户当前位置附近。 */
    private boolean isNearCurrentQuery(String query) {
        return query.startsWith("附近")
                || query.startsWith("周边")
                || query.startsWith("当前位置")
                || query.startsWith("就近")
                || query.contains("我附近")
                || query.contains("我身边")
                || query.contains("当前位置");
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
            if (StringUtils.hasText(city) && address.equals(city)) {
                return new FoodSearchIntent(
                        FoodSearchIntentTypeEnum.CITY_KEYWORD,
                        city,
                        null,
                        defaultKeyword(keywords));
            }
            if (StringUtils.hasText(city) && address.startsWith(city)) {
                address = address.substring(city.length());
            }

            return new FoodSearchIntent(
                    FoodSearchIntentTypeEnum.NEAR_ADDRESS,
                    city,
                    address,
                    defaultKeyword(keywords));
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
        return new FoodSearchIntent(
                FoodSearchIntentTypeEnum.CITY_KEYWORD,
                city,
                null,
                defaultKeyword(keywords));
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

    /** 保守清理 query 中无实际搜索意义的口语词，避免误删“西餐厅”等有效关键词。 */
    private String cleanupKeywords(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }

        String cleaned = value.trim();
        cleaned = cleaned.replace("帮我找", "");
        cleaned = cleaned.replace("给我找", "");
        cleaned = cleaned.replace("帮我", "");
        cleaned = cleaned.replace("给我", "");
        cleaned = cleaned.replace("找一下", "");
        cleaned = cleaned.replace("找个", "");
        cleaned = cleaned.replace("查一下", "");
        cleaned = cleaned.replace("推荐一下", "");
        cleaned = cleaned.replace("有什么好吃的", "");
        cleaned = cleaned.replace("有什么吃的", "");
        cleaned = cleaned.replace("有什么", "");
        cleaned = cleaned.replace("推荐", "");
        cleaned = cleaned.replaceFirst("^[找查的]+", "");
        return cleaned.trim();
    }

    /** 没有明确关键词时，默认查“美食”。 */
    private String defaultKeyword(String keywords) {
        return StringUtils.hasText(keywords) ? keywords : "美食";
    }

    // ==================== 高德接口调用区 ====================

    /**
     * 根据意图选择对应的高德查询方式。
     *
     * <p>NEAR_CURRENT 和 NEAR_ADDRESS 走周边搜索，CITY_KEYWORD 走关键字搜索。
     */
    private SearchResult executeSearch(
            FoodSearchIntent intent,
            String currentLocation,
            Integer radius,
            Integer pageSize,
            Integer pageNum) {
        if (intent.intentType() == FoodSearchIntentTypeEnum.NEAR_CURRENT) {
            if (!StringUtils.hasText(currentLocation)) {
                throw new IllegalArgumentException(
                        "请先允许获取当前位置，或输入具体地点，例如：洪崖洞附近火锅");
            }
            JSONObject amapResponse =
                    amapFoodClient.searchAround(
                            currentLocation,
                            intent.keywords(),
                            intent.city(),
                            radius,
                            pageSize,
                            pageNum);
            return new SearchResult("AROUND", currentLocation, "当前位置", amapResponse);
        }

        if (intent.intentType() == FoodSearchIntentTypeEnum.NEAR_ADDRESS) {
            if (!StringUtils.hasText(intent.address())) {
                throw new IllegalArgumentException("请补充具体地点，例如：洪崖洞附近火锅");
            }
            String centerLocation =
                    amapFoodClient.geocodeToLocation(intent.address(), intent.city());
            JSONObject amapResponse =
                    amapFoodClient.searchAround(
                            centerLocation,
                            intent.keywords(),
                            intent.city(),
                            radius,
                            pageSize,
                            pageNum);
            return new SearchResult("AROUND", centerLocation, "搜索地点", amapResponse);
        }

        if (intent.intentType() == FoodSearchIntentTypeEnum.CITY_KEYWORD) {
            if (!StringUtils.hasText(intent.city()) || !StringUtils.hasText(intent.keywords())) {
                throw new IllegalArgumentException("请补充城市和美食关键词，例如：重庆火锅");
            }
            JSONObject amapResponse =
                    amapFoodClient.searchText(
                            intent.city(), intent.keywords(), pageSize, pageNum);
            return new SearchResult("TEXT", null, "搜索地点", amapResponse);
        }

        throw new IllegalArgumentException("暂不支持该美食查询意图");
    }

    // ==================== 响应构建区 ====================

    /** 把高德查询结果转换成工具统一返回对象。 */
    private FoodRecommendResponse buildResponse(
            FoodSearchIntent intent, SearchResult searchResult) {
        JSONObject amapResponse = searchResult.amapResponse();
        if (!amapFoodClient.isAmapSuccess(amapResponse)) {
            return FoodRecommendResponse.fail("高德 API 调用失败：" + text(amapResponse, "info"));
        }

        JSONArray pois = amapResponse.getJSONArray("pois");
        if (pois == null || pois.isEmpty()) {
            return buildEmptyResponse(intent, searchResult, "未找到符合条件的饭店");
        }

        List<FoodRestaurantItemDTO> items = new ArrayList<>();
        for (int i = 0; i < pois.size(); i++) {
            JSONObject poi = pois.getJSONObject(i);
            if (isValidFoodPoi(poi)) {
                items.add(toFoodItem(poi, searchResult.distanceTargetText()));
            }
        }

        if (items.isEmpty()) {
            return buildEmptyResponse(intent, searchResult, "未找到符合条件的餐饮饭店");
        }

        fillTemplateReasons(items);
        return buildSuccessResponse(intent, searchResult, items);
    }

    /** 构建没有符合条件饭店时的成功空结果。 */
    private FoodRecommendResponse buildEmptyResponse(
            FoodSearchIntent intent, SearchResult searchResult, String message) {
        return new FoodRecommendResponse(
                true,
                message,
                "AMAP",
                searchResult.queryType(),
                intent.intentType(),
                searchResult.centerLocation(),
                0,
                List.of());
    }

    /** 构建包含饭店列表的成功结果。 */
    private FoodRecommendResponse buildSuccessResponse(
            FoodSearchIntent intent,
            SearchResult searchResult,
            List<FoodRestaurantItemDTO> items) {
        return new FoodRecommendResponse(
                true,
                "success",
                "AMAP",
                searchResult.queryType(),
                intent.intentType(),
                searchResult.centerLocation(),
                items.size(),
                items);
    }

    // ==================== POI 转 DTO 区 ====================

    /** 判断高德 POI 是否真的是餐饮类结果。 */
    private boolean isValidFoodPoi(JSONObject poi) {
        if (!StringUtils.hasText(text(poi, "name"))
                || !StringUtils.hasText(text(poi, "location"))) {
            return false;
        }
        String type = text(poi, "type");
        String typeCode = text(poi, "typecode");
        return (StringUtils.hasText(type) && type.contains("餐饮服务"))
                || (StringUtils.hasText(typeCode) && typeCode.startsWith("05"));
    }

    /** 高德 POI 转成饭店推荐 DTO。 */
    private FoodRestaurantItemDTO toFoodItem(JSONObject poi, String distanceTargetText) {
        String location = text(poi, "location");
        LocationPair locationPair = parseLocation(location);
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
                locationPair.longitude(),
                locationPair.latitude(),
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

    /** 把高德“经度,纬度”字符串安全转换为经纬度对象。 */
    private LocationPair parseLocation(String location) {
        if (!StringUtils.hasText(location) || !location.contains(",")) {
            return new LocationPair(null, null);
        }
        String[] parts = location.split(",");
        BigDecimal longitude = parseDecimal(parts[0]);
        BigDecimal latitude = parts.length > 1 ? parseDecimal(parts[1]) : null;
        return new LocationPair(longitude, latitude);
    }

    /** 从多个标签字段中提取一个较短的美食类型。 */
    private String extractFoodType(String keyTag, String recTag, String tag, String type) {
        String source = firstText(keyTag, recTag, tag, type);
        if (!StringUtils.hasText(source)) {
            return null;
        }
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

    /** 优先从 business 扩展字段取值，取不到再从 POI 根字段取值。 */
    private String valueFromBusinessOrPoi(
            JSONObject business, JSONObject poi, String businessKey, String poiKey) {
        if (business != null && StringUtils.hasText(text(business, businessKey))) {
            return text(business, businessKey);
        }
        return StringUtils.hasText(poiKey) ? text(poi, poiKey) : null;
    }

    // ==================== 推荐理由生成区 ====================

    /** 给每个饭店填充模板推荐理由。 */
    private void fillTemplateReasons(List<FoodRestaurantItemDTO> items) {
        for (FoodRestaurantItemDTO item : items) {
            item.setAiRecommendReason(buildTemplateReason(item));
        }
    }

    /** 使用模板生成推荐理由，只拼接高德真实返回字段。 */
    private String buildTemplateReason(FoodRestaurantItemDTO item) {
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
            parts.add("高德评分" + item.getRating());
        }
        if (StringUtils.hasText(item.getAvgCost())) {
            parts.add("人均约" + item.getAvgCost() + "元");
        }
        if (parts.isEmpty()) {
            return "高德地图返回的餐饮地点，可作为本次美食查询的候选。";
        }
        return String.join("，", parts) + "，可作为本次美食查询的候选。";
    }

    // ==================== 通用工具方法区 ====================

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

    // ==================== 内部对象区 ====================

    /** Service 内部暂存 query 解析结果的意图对象，非对外 DTO。 */
    private record FoodSearchIntent(
            FoodSearchIntentTypeEnum intentType,
            String city,
            String address,
            String keywords) {}

    /** Service 内部暂存一次高德查询结果及其响应上下文。 */
    private record SearchResult(
            String queryType,
            String centerLocation,
            String distanceTargetText,
            JSONObject amapResponse) {}

    /** Service 内部暂存解析后的经纬度。 */
    private record LocationPair(BigDecimal longitude, BigDecimal latitude) {}
}
