package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.dto.model.FoodRestaurantItemDTO;
import com.sora.aitravel.dto.response.FoodRecommendResponse;
import com.sora.aitravel.service.FoodRecommendService;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 美食真实数据获取节点。
 *
 * <p>读取每天的 FOOD 查询计划并调用 {@link FoodRecommendService}。真实查询失败或没有结果时，将城市基础数据池中的模拟美食候选转换为统一响应，
 * 保证 Generate 工作流不中断。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FoodRecommendNode {

    private static final int DEFAULT_RADIUS = 1500;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int DEFAULT_PAGE_NUM = 1;

    private final FoodRecommendService foodRecommendService;

    /**
     * 获取每天的美食推荐数据。
     *
     * <p>节点只负责调用美食服务和保存结果，不修改 DayDataFetchNode，也不参与后续候选排序和行程生成。
     */
    public void execute(GenerateWorkflowContext context) {
        Map<Integer, FoodRecommendResponse> recommendationsByDay = new LinkedHashMap<>();
        if (context.getDayQueryPlans() == null || context.getDayQueryPlans().isEmpty()) {
            context.setFoodRecommendationsByDay(recommendationsByDay);
            log.warn("节点[food-recommend]：没有逐日查询计划，跳过美食查询");
            return;
        }

        for (DayQueryPlan plan : context.getDayQueryPlans()) {
            QueryItem foodQuery = findFoodQuery(plan);
            if (foodQuery == null) {
                log.info("节点[food-recommend]：第 {} 天没有 FOOD 查询，跳过", plan.day());
                continue;
            }

            String query = buildQuery(foodQuery, context);
            FoodRecommendResponse response = queryFood(plan.day(), query, context);
            recommendationsByDay.put(plan.day(), response);
        }

        context.setFoodRecommendationsByDay(recommendationsByDay);
    }

    /** 查找当天第一个 FOOD 查询计划。 */
    private QueryItem findFoodQuery(DayQueryPlan plan) {
        if (plan == null || plan.queries() == null) {
            return null;
        }
        for (QueryItem query : plan.queries()) {
            if (query != null && "FOOD".equals(query.type())) {
                return query;
            }
        }
        return null;
    }

    /** 优先使用查询计划中的关键词，缺失时使用“城市 + 美食推荐”。 */
    private String buildQuery(QueryItem foodQuery, GenerateWorkflowContext context) {
        if (StringUtils.hasText(foodQuery.keyword())) {
            return foodQuery.keyword();
        }
        String city = foodQuery.getCity();
        if (!StringUtils.hasText(city)
                && context.getCityProfile() != null
                && StringUtils.hasText(context.getCityProfile().destination())) {
            city = context.getCityProfile().destination();
        }
        return StringUtils.hasText(city) ? city + "美食推荐" : "美食推荐";
    }

    /** 调用真实美食服务；失败、空结果或异常时统一降级为 mock 响应。 */
    private FoodRecommendResponse queryFood(
            Integer day, String query, GenerateWorkflowContext context) {
        try {
            FoodRecommendResponse response =
                    foodRecommendService.recommend(
                            query, "", DEFAULT_RADIUS, DEFAULT_PAGE_SIZE, DEFAULT_PAGE_NUM);
            if (isUsable(response)) {
                log.info(
                        "节点[food-recommend]：第 {} 天使用高德真实美食数据，query={}，total={}",
                        day,
                        query,
                        response.getTotal());
                return response;
            }

            String reason =
                    response == null
                            ? "美食服务返回 null"
                            : "美食服务无可用结果，success="
                                    + response.getSuccess()
                                    + "，message="
                                    + response.getMessage();
            return buildMockResponse(day, query, context, reason);
        } catch (RuntimeException exception) {
            log.warn(
                    "节点[food-recommend]：第 {} 天真实美食查询异常，query={}，切换 mock 兜底",
                    day,
                    query,
                    exception);
            return buildMockResponse(day, query, context, exception.getMessage());
        }
    }

    /** 真实响应必须成功且包含至少一家饭店。 */
    private boolean isUsable(FoodRecommendResponse response) {
        return response != null
                && Boolean.TRUE.equals(response.getSuccess())
                && response.getList() != null
                && !response.getList().isEmpty();
    }

    /** 将 CityProfile 中的模拟候选转换成美食模块统一响应。 */
    private FoodRecommendResponse buildMockResponse(
            Integer day, String query, GenerateWorkflowContext context, String fallbackReason) {
        List<FoodRestaurantItemDTO> mockItems = mockItems(context);
        log.warn(
                "节点[food-recommend]：第 {} 天使用 mock 美食兜底数据，query={}，reason={}，total={}",
                day,
                query,
                fallbackReason,
                mockItems.size());
        return new FoodRecommendResponse(
                true, "使用 mock 美食兜底数据", "MOCK", "MOCK", null, null, mockItems.size(), mockItems);
    }

    /** mock 数据只映射已有字段，评分、人均和营业时间等未知信息保持为空。 */
    private List<FoodRestaurantItemDTO> mockItems(GenerateWorkflowContext context) {
        if (context.getCityProfile() == null
                || context.getCityProfile().foodCandidates() == null) {
            return List.of();
        }
        return context.getCityProfile().foodCandidates().stream()
                .map(this::toMockFoodItem)
                .toList();
    }

    /** 将工作流统一 POI 候选转换成饭店 DTO。 */
    private FoodRestaurantItemDTO toMockFoodItem(PoiCandidate candidate) {
        BigDecimal longitude = null;
        BigDecimal latitude = null;
        if (candidate != null
                && StringUtils.hasText(candidate.getLocation())
                && candidate.getLocation().contains(",")) {
            String[] parts = candidate.getLocation().split(",");
            longitude = parseDecimal(parts[0]);
            latitude = parts.length > 1 ? parseDecimal(parts[1]) : null;
        }

        return new FoodRestaurantItemDTO(
                candidate == null ? null : candidate.getSourcePoiId(),
                candidate == null ? null : candidate.getName(),
                candidate == null ? null : candidate.getAddress(),
                null,
                candidate == null ? null : candidate.getArea(),
                null,
                null,
                null,
                null,
                candidate == null ? null : candidate.getLocation(),
                longitude,
                latitude,
                candidate == null ? null : candidate.getDistanceMeters(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                candidate == null ? null : candidate.getArea(),
                null,
                candidate == null ? null : candidate.getReason(),
                "");
    }

    /** 安全转换经纬度数字，格式异常时保持为空。 */
    private BigDecimal parseDecimal(String value) {
        try {
            return StringUtils.hasText(value) ? new BigDecimal(value.trim()) : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
