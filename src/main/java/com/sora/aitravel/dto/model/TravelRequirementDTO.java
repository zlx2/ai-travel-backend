package com.sora.aitravel.dto.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;

/**
 * 旅行需求 DTO（结构化用户旅行需求）。
 *
 * <p>记录用户输入的完整旅行需求信息，包含出发地、目的地、天数、预算、偏好等。
 *
 * @param departure 出发地
 * @param destination 目的地
 * @param days 行程天数（1-7天）
 * @param budget 预算金额
 * @param budgetType 预算类型（如 total-总预算、daily-每日预算）
 * @param peopleCount 出行人数
 * @param preferences 偏好标签列表（如 ["美食", "自然风光", "文化体验"]）
 * @param pace 行程节奏（如 relaxing-休闲、balanced-适中、intensive-紧凑）
 * @param avoidances 避雷/不喜欢的内容列表
 * @param travelDate 出行日期（格式：yyyy-MM-dd）
 */
public record TravelRequirementDTO(
        String departure,
        String destination,
        String routeMode,
        String routeStructure,
        String routeRegion,
        List<String> routeCities,
        String transportMode,
        String rentalIntent,
        RentalRequirementDTO rentalRequirement,
        @Min(1) @Max(7) Integer days,
        Integer budget,
        String budgetType,
        Integer peopleCount,
        List<String> preferences,
        String pace,
        List<String> avoidances,
        String travelDate) {}
