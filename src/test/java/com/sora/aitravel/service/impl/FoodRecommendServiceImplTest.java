package com.sora.aitravel.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.sora.aitravel.client.amap.AmapFoodClient;
import com.sora.aitravel.common.enums.FoodSearchIntentTypeEnum;
import com.sora.aitravel.config.AmapProperties;
import com.sora.aitravel.dto.model.FoodRestaurantItemDTO;
import com.sora.aitravel.dto.response.FoodRecommendResponse;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 美食推荐实现类第一阶段修复测试。
 *
 * <p>本测试不调用真实高德接口，也不需要配置 AMAP_API_KEY，主要检查以下六项内容：
 *
 * <ol>
 *   <li>“城市 + 附近 + 关键词”能否正确识别；
 *   <li>“城市 + 具体地点 + 附近 + 关键词”能否正确识别；
 *   <li>当前位置附近的常见口语表达能否正确识别；
 *   <li>关键词清理是否会误删“餐厅”等有效内容；
 *   <li>高德结果被全部过滤后，能否返回合理的空结果；
 *   <li>推荐理由是否只使用实际存在的高德字段。
 * </ol>
 *
 * <p>每个测试都会在控制台打印“用户输入、预期结果、实际结果”。如果结果不同，测试会直接抛出带中文说明的异常。
 */
class FoodRecommendServiceImplTest {

    private FoodRecommendServiceImpl service;

    /** 每个测试开始前创建一个新的实现类对象，避免不同测试之间互相影响。 */
    @BeforeEach
    void setUp() {
        AmapFoodClient amapFoodClient = new AmapFoodClient(new AmapProperties());
        service = new FoodRecommendServiceImpl(amapFoodClient);
    }

    /** 测试“重庆附近火锅”不能被误认为“重庆这个具体地点附近”。 */
    @Test
    @DisplayName("城市附近美食应识别为城市关键词查询")
    void shouldTreatCityNearKeywordAsCityKeyword() {
        checkIntent(
                "重庆附近火锅",
                FoodSearchIntentTypeEnum.CITY_KEYWORD,
                "重庆",
                null,
                "火锅");
    }

    /** 测试带城市前缀的具体地点查询，并确认城市名称会从 address 中移除。 */
    @Test
    @DisplayName("城市具体地点附近应识别为地点附近查询")
    void shouldTreatCityAddressNearKeywordAsNearAddress() {
        checkIntent(
                "重庆洪崖洞附近火锅",
                FoodSearchIntentTypeEnum.NEAR_ADDRESS,
                "重庆",
                "洪崖洞",
                "火锅");
    }

    /** 测试“附近、我附近、我身边、当前位置、就近”等当前位置附近表达。 */
    @Test
    @DisplayName("常见当前位置附近表达应正确识别")
    void shouldRecognizeCurrentLocationQueries() {
        checkIntent(
                "附近有什么好吃的",
                FoodSearchIntentTypeEnum.NEAR_CURRENT,
                null,
                null,
                "美食");
        checkIntent(
                "我附近小吃",
                FoodSearchIntentTypeEnum.NEAR_CURRENT,
                null,
                null,
                "小吃");
        checkIntent(
                "帮我找我身边的火锅",
                FoodSearchIntentTypeEnum.NEAR_CURRENT,
                null,
                null,
                "火锅");
        checkIntent(
                "当前位置附近餐厅",
                FoodSearchIntentTypeEnum.NEAR_CURRENT,
                null,
                null,
                "餐厅");
        checkIntent(
                "就近找个饭店",
                FoodSearchIntentTypeEnum.NEAR_CURRENT,
                null,
                null,
                "饭店");
    }

    /** 测试关键词清理不能把“西餐厅”中的“餐厅”删除，避免最终只剩下“西”。 */
    @Test
    @DisplayName("关键词清理应保留西餐厅等有效词语")
    void shouldKeepRestaurantWordsWhenCleaningKeywords() {
        checkIntent(
                "西安西餐厅推荐",
                FoodSearchIntentTypeEnum.CITY_KEYWORD,
                "西安",
                null,
                "西餐厅");
    }

    /**
     * 模拟高德返回一个景区 POI。
     *
     * <p>原始 pois 不为空，但这个 POI 不属于餐饮类型，会被 isValidFoodPoi 过滤。过滤后应返回成功的空列表，而不是普通 success。
     */
    @Test
    @DisplayName("POI全部被过滤后应返回明确的空结果")
    void shouldReturnEmptySuccessWhenAllPoisAreFilteredOut() {
        Object intent = parseIntent("重庆火锅推荐");
        JSONObject invalidPoi =
                new JSONObject()
                        .set("name", "洪崖洞景区")
                        .set("location", "106.579,29.557")
                        .set("type", "风景名胜")
                        .set("typecode", "110000");
        JSONObject amapResponse =
                new JSONObject()
                        .set("status", "1")
                        .set("infocode", "10000")
                        .set("pois", new JSONArray().put(invalidPoi));

        FoodRecommendResponse response =
                ReflectionTestUtils.invokeMethod(
                        service,
                        "buildResponse",
                        intent,
                        createSearchResult("TEXT", null, "搜索地点", amapResponse));

        if (response == null) {
            throw new IllegalStateException("空结果测试失败：buildResponse 返回了 null");
        }

        System.out.println();
        System.out.println("========== POI 过滤后空结果测试 ==========");
        printExpectedAndActual("success", true, response.getSuccess());
        printExpectedAndActual("message", "未找到符合条件的餐饮饭店", response.getMessage());
        printExpectedAndActual("total", 0, response.getTotal());
        printExpectedAndActual("list 是否为空", true, response.getList().isEmpty());

        checkValue("success", true, response.getSuccess());
        checkValue("message", "未找到符合条件的餐饮饭店", response.getMessage());
        checkValue("total", 0, response.getTotal());
        checkValue("list 是否为空", true, response.getList().isEmpty());
    }

    /**
     * 只给饭店设置距离和类型，不设置评分、人均、营业时间。
     *
     * <p>用于确认模板理由只会使用已有字段，不会凭空生成评分、人均或营业时间。
     */
    @Test
    @DisplayName("模板推荐理由只能使用已有的高德字段")
    void shouldBuildReasonOnlyFromAvailableAmapFields() {
        FoodRestaurantItemDTO item = new FoodRestaurantItemDTO();
        item.setDistanceText("距当前位置约300米");
        item.setFoodType("火锅店");

        String reason = ReflectionTestUtils.invokeMethod(service, "buildTemplateReason", item);

        String expected = "距当前位置约300米，主打火锅店，可作为本次美食查询的候选。";

        System.out.println();
        System.out.println("========== 模板推荐理由测试 ==========");
        printExpectedAndActual("推荐理由", expected, reason);

        checkValue("推荐理由", expected, reason);
    }

    /** 调用实现类中的规则解析方法，获取本次测试的实际意图解析结果。 */
    private Object parseIntent(String query) {
        Object intent = ReflectionTestUtils.invokeMethod(service, "parseIntentByRule", query);
        if (intent == null) {
            throw new IllegalStateException("意图解析失败：输入“" + query + "”后没有得到解析结果");
        }
        return intent;
    }

    /** 创建实现类内部使用的 SearchResult，只用于测试 buildResponse。 */
    private Object createSearchResult(
            String queryType,
            String centerLocation,
            String distanceTargetText,
            JSONObject amapResponse) {
        try {
            Class<?> searchResultClass =
                    Class.forName(
                            "com.sora.aitravel.service.impl.FoodRecommendServiceImpl$SearchResult");
            var constructor =
                    searchResultClass.getDeclaredConstructor(
                            String.class, String.class, String.class, JSONObject.class);
            constructor.setAccessible(true);
            return constructor.newInstance(
                    queryType, centerLocation, distanceTargetText, amapResponse);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("测试准备失败：无法创建 SearchResult", exception);
        }
    }

    /** 打印并检查一条用户输入的意图类型、城市、地点和关键词。 */
    private void checkIntent(
            String query,
            FoodSearchIntentTypeEnum intentType,
            String city,
            String address,
            String keywords) {
        Object intent = parseIntent(query);
        Object actualIntentType = ReflectionTestUtils.getField(intent, "intentType");
        Object actualCity = ReflectionTestUtils.getField(intent, "city");
        Object actualAddress = ReflectionTestUtils.getField(intent, "address");
        Object actualKeywords = ReflectionTestUtils.getField(intent, "keywords");

        System.out.println();
        System.out.println("========== 意图解析测试 ==========");
        System.out.println("用户输入：" + query);
        printExpectedAndActual("intentType", intentType, actualIntentType);
        printExpectedAndActual("city", city, actualCity);
        printExpectedAndActual("address", address, actualAddress);
        printExpectedAndActual("keywords", keywords, actualKeywords);

        checkValue("intentType", intentType, actualIntentType);
        checkValue("city", city, actualCity);
        checkValue("address", address, actualAddress);
        checkValue("keywords", keywords, actualKeywords);
    }

    /** 统一打印预期值和程序实际返回值，方便直接查看测试过程。 */
    private void printExpectedAndActual(String fieldName, Object expected, Object actual) {
        System.out.println(fieldName + "：预期=" + expected + "，实际=" + actual);
    }

    /** 使用普通条件判断检查结果；如果不一致，抛出带中文说明的异常使测试失败。 */
    private void checkValue(String fieldName, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new IllegalStateException(
                    fieldName + " 检查失败：预期值为“" + expected + "”，实际值为“" + actual + "”");
        }
    }
}
