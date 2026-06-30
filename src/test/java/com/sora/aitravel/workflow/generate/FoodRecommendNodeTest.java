package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.dto.model.FoodRestaurantItemDTO;
import com.sora.aitravel.dto.response.FoodRecommendResponse;
import com.sora.aitravel.service.FoodRecommendService;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 美食真实数据节点测试。
 *
 * <p>使用本地 Stub Service 模拟真实成功、失败、空列表和异常，不调用高德网络接口。
 */
class FoodRecommendNodeTest {

    private StubFoodRecommendService foodRecommendService;
    private FoodRecommendNode node;

    @BeforeEach
    void setUp() {
        foodRecommendService = new StubFoodRecommendService();
        node = new FoodRecommendNode(foodRecommendService);
    }

    /** 多天查询成功时，每一天都应保存对应的真实响应。 */
    @Test
    @DisplayName("多天真实美食查询结果按天保存")
    void shouldStoreRealResponsesByDay() {
        foodRecommendService.addResponse(realResponse("第一天真实饭店"));
        foodRecommendService.addResponse(realResponse("第二天真实饭店"));
        GenerateWorkflowContext context = contextWithPlans(true, true);

        node.execute(context);

        Map<Integer, FoodRecommendResponse> results = context.getFoodRecommendationsByDay();
        checkValue("结果天数", 2, results.size());
        checkValue("第一天来源", "AMAP", results.get(1).getSource());
        checkValue("第一天饭店", "第一天真实饭店", results.get(1).getList().get(0).getName());
        checkValue("第二天饭店", "第二天真实饭店", results.get(2).getList().get(0).getName());
        checkValue("服务调用次数", 2, foodRecommendService.getCallCount());
    }

    /** Service 明确返回失败时，节点保存失败结果，不编造餐饮数据。 */
    @Test
    @DisplayName("服务失败时不编造餐饮数据")
    void shouldNotInventFoodWhenServiceReturnsFailure() {
        foodRecommendService.addResponse(FoodRecommendResponse.fail("高德调用失败"));
        GenerateWorkflowContext context = contextWithPlans(true);

        node.execute(context);

        FoodRecommendResponse response = context.getFoodRecommendationsByDay().get(1);
        checkFailedResponse(response);
    }

    /** Service 调用成功但没有饭店时，节点保存失败结果，不编造餐饮数据。 */
    @Test
    @DisplayName("真实结果为空时不编造餐饮数据")
    void shouldNotInventFoodWhenRealListIsEmpty() {
        foodRecommendService.addResponse(
                new FoodRecommendResponse(
                        true, "未找到符合条件的饭店", "AMAP", "TEXT", null, null, 0, List.of()));
        GenerateWorkflowContext context = contextWithPlans(true);

        node.execute(context);

        FoodRecommendResponse response = context.getFoodRecommendationsByDay().get(1);
        checkFailedResponse(response);
    }

    /** Service 抛出异常时节点不能继续向外抛错，也不能编造餐饮数据。 */
    @Test
    @DisplayName("服务异常时不中断工作流且不编造餐饮数据")
    void shouldNotInventFoodWhenServiceThrowsException() {
        foodRecommendService.addException(new IllegalStateException("网络异常"));
        GenerateWorkflowContext context = contextWithPlans(true);

        node.execute(context);

        FoodRecommendResponse response = context.getFoodRecommendationsByDay().get(1);
        checkFailedResponse(response);
    }

    /** 当天没有 FOOD 查询时应跳过，不调用美食服务，也不为当天写入结果。 */
    @Test
    @DisplayName("没有FOOD查询时跳过当天")
    void shouldSkipDayWithoutFoodQuery() {
        GenerateWorkflowContext context = contextWithPlans(false);

        node.execute(context);

        checkValue("结果是否为空", true, context.getFoodRecommendationsByDay().isEmpty());
        checkValue("服务调用次数", 0, foodRecommendService.getCallCount());
    }

    /** 服务失败时不应把 CityProfile 里的候选当成 mock 推荐写回。 */
    @Test
    @DisplayName("失败时不写入mock饭店列表")
    void shouldNotWriteMockRestaurantList() {
        foodRecommendService.addResponse(FoodRecommendResponse.fail("使用兜底"));
        GenerateWorkflowContext context = contextWithPlans(true);

        node.execute(context);

        FoodRecommendResponse response = context.getFoodRecommendationsByDay().get(1);
        checkFailedResponse(response);
    }

    /** 构造带指定天数查询计划的工作流上下文。 */
    private GenerateWorkflowContext contextWithPlans(boolean... includeFoodQuery) {
        GenerateWorkflowContext context = new GenerateWorkflowContext();
        context.setCityProfile(
                new CityProfile(
                        "重庆",
                        List.of("核心商圈"),
                        List.of(),
                        List.of(),
                        List.of(
                                new PoiCandidate(
                                        "FOOD",
                                        "重庆本地小吃街",
                                        "老城区域模拟地址",
                                        "老城区域",
                                        "重庆",
                                        "106.570000,29.550000",
                                        "SIMULATED_AMAP",
                                        "MOCK_FOOD_1",
                                        "选择多，适合午餐或下午茶。",
                                        null,
                                        "050000",
                                        null,
                                        "10:00-22:00",
                                        "4.5",
                                        80,
                                        "核心商圈",
                                        List.of("本地小吃"),
                                        null,
                                        List.of())),
                        List.of()));

        List<DayQueryPlan> plans = new ArrayList<>();
        for (int i = 0; i < includeFoodQuery.length; i++) {
            int day = i + 1;
            QueryItem query =
                    includeFoodQuery[i]
                            ? new QueryItem(
                                    "FOOD",
                                    "重庆第" + day + "天美食",
                                    "重庆",
                                    "重庆核心商圈",
                                    null,
                                    null,
                                    "查询午餐和晚餐候选")
                            : new QueryItem("SCENIC", "重庆景点", "重庆", "重庆核心商圈", null, null, "查询景点");
            plans.add(new DayQueryPlan(day, List.of(query)));
        }
        context.setDayQueryPlans(plans);
        return context;
    }

    /** 构造包含一家真实饭店的成功响应。 */
    private FoodRecommendResponse realResponse(String name) {
        FoodRestaurantItemDTO item = new FoodRestaurantItemDTO();
        item.setAmapPoiId("AMAP_" + name);
        item.setName(name);
        item.setAddress("真实地址");
        item.setLocation("106.580000,29.560000");
        item.setAiRecommendReason("高德真实推荐理由");
        return new FoodRecommendResponse(
                true, "success", "AMAP", "TEXT", null, null, 1, List.of(item));
    }

    /** 检查失败响应不会携带伪造饭店列表。 */
    private void checkFailedResponse(FoodRecommendResponse response) {
        if (response == null) {
            throw new IllegalStateException("失败响应缺失：响应为 null");
        }
        checkValue("是否成功", false, response.getSuccess());
        checkValue("饭店列表是否为空", true, response.getList() == null || response.getList().isEmpty());
    }

    /** 使用普通条件判断检查结果，不一致时抛出中文异常。 */
    private void checkValue(String fieldName, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new IllegalStateException(
                    fieldName + " 检查失败：预期值为“" + expected + "”，实际值为“" + actual + "”");
        }
    }

    /** 可按调用顺序返回响应或抛出异常的本地 Stub Service。 */
    private static class StubFoodRecommendService implements FoodRecommendService {

        private final Deque<Object> results = new ArrayDeque<>();
        private int callCount;

        void addResponse(FoodRecommendResponse response) {
            results.addLast(response);
        }

        void addException(RuntimeException exception) {
            results.addLast(exception);
        }

        int getCallCount() {
            return callCount;
        }

        @Override
        public FoodRecommendResponse recommend(
                String query,
                String currentLocation,
                Integer radius,
                Integer pageSize,
                Integer pageNum) {
            callCount++;
            if (results.isEmpty()) {
                throw new IllegalStateException("未配置 Stub 返回结果");
            }
            Object result = results.removeFirst();
            if (result instanceof RuntimeException exception) {
                throw exception;
            }
            return (FoodRecommendResponse) result;
        }
    }
}
