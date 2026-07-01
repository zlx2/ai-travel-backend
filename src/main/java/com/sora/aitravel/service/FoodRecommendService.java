package com.sora.aitravel.service;

import com.sora.aitravel.dto.response.FoodRecommendResponse;

/** 美食饭店推荐业务服务，供后续工作流节点调用。 */
public interface FoodRecommendService {

    FoodRecommendResponse recommend(
            String query,
            String currentLocation,
            Integer radius,
            Integer pageSize,
            Integer pageNum);
}
