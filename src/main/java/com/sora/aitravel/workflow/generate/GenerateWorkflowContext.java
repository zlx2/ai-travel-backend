package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.dto.model.RecommendationContextDTO;
import com.sora.aitravel.dto.model.RentalQuoteOptionDTO;
import com.sora.aitravel.dto.model.RentalTripContextDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.model.TripPlanDTO;
import com.sora.aitravel.dto.request.TripGenerateRequest;
import com.sora.aitravel.dto.response.FoodRecommendResponse;
import com.sora.aitravel.dto.response.TripGenerateResponse;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 行程生成上下文，由准备阶段编排器和单日生成编排器顺序传递。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateWorkflowContext {

    /** 当前操作用户的 ID，用于权限校验和后续行程归属。 */
    private Long userId;

    /** 用户提交的行程生成请求，包含出发地、目的地、天数等必填参数。 */
    private TripGenerateRequest request;

    /** 已确认的结构化旅行需求。 */
    private TravelRequirementDTO requirement;

    /** 行程生成使用的租车报价；仅使用用户已确认并传入的报价。 */
    private RentalQuoteOptionDTO selectedQuote;

    /** 租车行程上下文，包含接车点、送车方案、路线结构和驾驶偏好。 */
    private RentalTripContextDTO rentalTripContext;

    /** 整体行程骨架，只描述每天主题和区域，不代表最终地点推荐。 */
    private List<DaySkeleton> daySkeletons;

    /** 城市基础数据池，来源于真实 POI 查询结果。 */
    private CityProfile cityProfile;

    /** 新版生成候选池，包含真实景点和区域锚点。 */
    private CandidatePool candidatePool;

    /** AI 生成的多日路线骨架候选方案。 */
    private List<MacroRoutePlan> macroRoutePlans;

    /** 高德补充的多日路线事实。 */
    private List<MacroRouteFact> macroRouteFacts;

    /** AI 审稿后选中的路线骨架。 */
    private RouteCriticResult routeCriticResult;

    /** 目的地天气预报数据（由 WeatherTool 提供）。 */
    private String weatherForecast;

    /** 酒店搜索数据（由 HotelTool 提供）。 */
    private String hotelSearchResult;

    /** 每天生成时所需上下文。 */
    private List<DayContext> dayContexts;

    /** 每天需要调用工具查询的数据计划。 */
    private List<DayQueryPlan> dayQueryPlans;

    /** 按天保存的美食推荐结果；真实查询失败时保存失败响应，不伪造餐饮数据。 */
    private Map<Integer, FoodRecommendResponse> foodRecommendationsByDay;

    /** 每天通过工具查询并经后端清洗后的候选数据。 */
    private List<DayDataPackage> rankedDayDataPackages;

    /** 每天行程校验报告。 */
    private List<DayPlanValidationReport> dayValidationReports;

    /** 已通过校验并锁定的每日行程。 */
    private List<TripPlanDTO.DailyPlan> lockedDailyPlans;

    /** 行程生成前准备好的景点、美食、住宿和交通推荐上下文。 */
    private RecommendationContextDTO recommendationContext;

    /** 最终的结构化行程生成结果（包含每日计划等），供 Controller 返回给前端。 */
    private TripGenerateResponse result;

    /** 当前是否只生成单日行程。 */
    private Boolean singleDayGeneration;

    /** 当前单日生成的目标天数。 */
    private Integer targetDayNo;

    /** 单日生成前已经生成的前序天行程，用于跨天衔接。 */
    private List<TripPlanDTO.DailyPlan> previousDailyPlans;

    /** 用户对当前单日行程的追加调整要求，仅按天重新生成时使用。 */
    private String revisionText;

    /**
     * 是否有景点候选。
     * @return
     */
    public boolean hasScenicCandidates() {
        return cityProfile != null
                && cityProfile.scenicCandidates() != null
                && !cityProfile.scenicCandidates().isEmpty();
    }

    /**
     * 是否单日行程生成。
     * @return
     */
    public boolean isSingleDayGeneration() {
        return Boolean.TRUE.equals(singleDayGeneration);
    }
}
