package com.sora.aitravel.dto.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TravelRequirementDTO {

    private String departure;
    private String destination;
    private String routeMode;
    private String routeStructure;
    private String routeRegion;
    private List<String> routeCities;
    private String transportMode;
    private String rentalIntent;
    private RentalRequirementDTO rentalRequirement;

    @Min(1) @Max(7) private Integer days;

    private Integer budget;
    private String budgetType;
    private Integer peopleCount;
    private List<String> preferences;
    private String pace;
    private List<String> avoidances;
    private String travelDate;
}
