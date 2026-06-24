package com.sora.aitravel.dto.model;

import java.util.List;

/**
 * 完整旅行计划 DTO。
 *
 * <p>由 AI 生成或用户保存的完整行程计划，包含每日安排、预算汇总和住宿建议。
 *
 * @param title 行程标题
 * @param destination 目的地
 * @param days 总天数
 * @param summary 行程摘要
 * @param dailyPlans 每日计划列表
 * @param budgetSummary 预算汇总
 * @param accommodationSuggestion 住宿建议
 * @param tips 整体旅行提示列表
 */
public record TripPlanDTO(
        String title,
        String destination,
        Integer days,
        String summary,
        List<DailyPlan> dailyPlans,
        BudgetSummary budgetSummary,
        AccommodationSuggestion accommodationSuggestion,
        List<String> tips) {

    /**
     * 每日计划 DTO。
     *
     * @param day 第几天（从1开始）
     * @param theme 当日主题
     * @param items 当日具体行程项目列表
     * @param foodSuggestions 餐饮推荐列表
     * @param estimatedCost 当日预估花费
     * @param dayTips 当日提示
     */
    public record DailyPlan(
            Integer day,
            String theme,
            String fromCity,
            String toCity,
            String overnightCity,
            Double driveHoursEstimate,
            Integer driveDistanceEstimate,
            List<PlanItem> items,
            List<String> foodSuggestions,
            Integer estimatedCost,
            List<String> dayTips) {}

    /**
     * 行程项目 DTO（每日计划中的单个活动）。
     *
     * @param time 活动时间（如 "09:00"）
     * @param place 活动地点/场所名称
     * @param activity 活动描述
     * @param duration 活动时长（如 "2小时"）
     * @param transport 接驳交通方式
     * @param cost 活动花费
     * @param tips 活动小贴士
     */
    public record PlanItem(
            String time,
            String place,
            String activity,
            String duration,
            String transport,
            Integer cost,
            String tips) {}

    /**
     * 预算汇总 DTO。
     *
     * @param totalEstimatedCost 预估总花费
     * @param transportCost 交通费用
     * @param foodCost 餐饮费用
     * @param ticketCost 门票费用
     * @param hotelCost 住宿费用
     * @param tips 预算相关提示
     */
    public record BudgetSummary(
            Integer totalEstimatedCost,
            Integer transportCost,
            Integer foodCost,
            Integer ticketCost,
            Integer hotelCost,
            String tips) {}

    /**
     * 住宿建议 DTO。
     *
     * @param area 推荐住宿区域
     * @param reason 推荐该区域的原因
     * @param priceRange 价格区间描述
     */
    public record AccommodationSuggestion(String area, String reason, String priceRange) {}
}
