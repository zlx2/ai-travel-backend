package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.dto.response.FoodRecommendResponse;
import com.sora.aitravel.service.FoodRecommendService;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 美食真实数据获取节点。
 *
 * <p>读取每天的 FOOD 查询计划并调用 {@link FoodRecommendService}。真实查询失败或没有结果时返回失败响应，不伪造餐饮数据。
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
            FoodRecommendResponse response = queryFood(plan.day(), query);
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

    /** 调用真实美食服务；失败、空结果或异常时返回失败响应。 */
    private FoodRecommendResponse queryFood(Integer day, String query) {
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
            log.warn("节点[food-recommend]：第 {} 天无可用真实美食数据，query={}，reason={}", day, query, reason);
            return FoodRecommendResponse.fail(reason);
        } catch (RuntimeException exception) {
            log.warn("节点[food-recommend]：第 {} 天真实美食查询异常，query={}", day, query, exception);
            return FoodRecommendResponse.fail("美食查询失败：" + exception.getMessage());
        }
    }

    /** 真实响应必须成功且包含至少一家饭店。 */
    private boolean isUsable(FoodRecommendResponse response) {
        return response != null
                && Boolean.TRUE.equals(response.getSuccess())
                && response.getList() != null
                && !response.getList().isEmpty();
    }
}
