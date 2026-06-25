package com.sora.aitravel.dto.response;

import com.sora.aitravel.dto.model.RecommendationContextDTO;
import com.sora.aitravel.dto.model.RentalQuoteOptionDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.model.TripPlanDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 旅行计划生成响应 DTO。
 *
 * @param conversationId AI 对话 ID
 * @param requirement 最终确认的结构化旅行需求
 * @param recommendationContext 行程生成前准备的景点、美食、住宿和交通推荐上下文
 * @param tripPlan AI 生成的完整旅行计划
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TripGenerateResponse {

    private String schemaVersion;
    private String conversationId;
    private TravelRequirementDTO requirement;
    private RentalQuoteOptionDTO selectedQuote;
    private RecommendationContextDTO recommendationContext;
    private TripPlanDTO tripPlan;
}
