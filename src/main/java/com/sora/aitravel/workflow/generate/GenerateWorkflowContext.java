package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.dto.model.RecommendationContextDTO;
import com.sora.aitravel.dto.model.RentalQuoteOptionDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.model.TripPlanDTO;
import com.sora.aitravel.dto.request.TripGenerateRequest;
import com.sora.aitravel.dto.response.TripGenerateResponse;
import java.util.List;
import lombok.Data;

/**
 * 生成工作流上下文。
 *
 * <p>贯穿整个 {@link TripGenerateWorkflow} 的数据容器，保存行程生成流程中 各节点所需的输入数据以及产生的中间/最终结果。
 *
 * <p>在整个工作流中的位置：{@link TripGenerateWorkflow} 的输入和输出载体。 工作流入口接收此上下文，依次传递给各 Spring AI Alibaba Graph
 * node，每个节点从中读取输入并写入产出。
 *
 * <p>注意：Generate 工作流不负责自动保存行程，持久化必须由用户随后调用 Trip 接口触发。
 *
 * <p>输入：{@link #userId}（用户ID）、{@link #request}（生成请求 DTO）。 中间产物：{@link #rawModelResponse}（模型原始响应）。
 * 输出：{@link #result}（生成结果 DTO）。 标记：{@link #repairAttempted}（是否已执行过一次 JSON 修复）。
 */
@Data
public class GenerateWorkflowContext {

    /** 当前操作用户的 ID，用于权限校验和后续行程归属。 */
    private Long userId;

    /** 用户提交的行程生成请求，包含出发地、目的地、天数等必填参数。 */
    private TripGenerateRequest request;

    /** 已确认的结构化旅行需求。 */
    private TravelRequirementDTO requirement;

    /** 行程生成使用的租车报价；租车业务未接入时可由模拟报价补齐。 */
    private RentalQuoteOptionDTO selectedQuote;

    /** 整体行程骨架，只描述每天主题和区域，不代表最终地点推荐。 */
    private List<DaySkeleton> daySkeletons;

    /** 城市基础数据池，后续会替换为真实工具查询结果。 */
    private CityProfile cityProfile;

    /** 目的地天气预报数据（由 WeatherTool 提供）。 */
    private String weatherForecast;

    /** 酒店搜索数据（由 HotelTool 提供）。 */
    private String hotelSearchResult;

    /** 每天生成时所需上下文。 */
    private List<DayContext> dayContexts;

    /** 每天需要调用工具查询的数据计划。 */
    private List<DayQueryPlan> dayQueryPlans;

    /** 每天通过工具查询并经后端清洗后的候选数据。 */
    private List<DayDataPackage> rankedDayDataPackages;

    /** 每天行程校验报告。 */
    private List<DayPlanValidationReport> dayValidationReports;

    /** 已通过校验并锁定的每日行程。 */
    private List<TripPlanDTO.DailyPlan> lockedDailyPlans;

    /** AI 模型返回的原始行程计划 JSON 字符串，由 TripPlanGenerateNode 调用模型后填充。 */
    private String rawModelResponse;

    /** 行程生成前准备好的景点、美食、住宿和交通推荐上下文。 */
    private RecommendationContextDTO recommendationContext;

    /** 推荐上下文格式化后的提示文本，供模型生成最终行程时参考。 */
    private String recommendationPromptContext;

    /** 最终的结构化行程生成结果（包含每日计划等），供 Controller 返回给前端。 */
    private TripGenerateResponse result;

    /** 标记是否已经执行过一次 JSON 修复。若为 true 则不再重复修复，避免死循环。 */
    private boolean repairAttempted;
}
