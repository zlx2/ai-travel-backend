package com.sora.aitravel.tools;

import com.sora.aitravel.dto.response.FoodRecommendResponse;
import com.sora.aitravel.service.FoodRecommendService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 美食饭店推荐 AI Tool。
 *
 * <p>这是给 Spring AI Alibaba 工作流或 Tool Calling 使用的入口。工具类只负责暴露可调用方法， 真实业务逻辑统一委托给 {@link
 * FoodRecommendService}。
 */
@Component
@RequiredArgsConstructor
public class FoodTool {

    private final FoodRecommendService foodRecommendService;

    @Tool(
            name = "recommendFood",
            description =
                    """
                    推荐当地美食和饭店。
                    适用于用户询问附近美食、某地点附近饭店、某城市特色美食等场景。
                    返回结构化饭店推荐数据，包含高德真实 POI 字段、距离、评分、人均、标签和推荐理由。
                    """)
    public FoodRecommendResponse recommendFood(
            @ToolParam(description = "用户原始美食查询，例如：附近的重庆火锅、洪崖洞附近小吃、重庆火锅推荐") String query,
            @ToolParam(description = "用户当前位置，经度,纬度；没有定位时传空字符串") String currentLocation,
            @ToolParam(description = "周边搜索半径，单位米；为空时默认 1500") Integer radius,
            @ToolParam(description = "每页数量；为空时默认 10，最大 25") Integer pageSize,
            @ToolParam(description = "页码；为空时默认 1") Integer pageNum) {
        return foodRecommendService.recommend(query, currentLocation, radius, pageSize, pageNum);
    }
}
