package com.sora.aitravel.dto.request;

import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.model.TripPlanDTO;
import jakarta.validation.constraints.*;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 保存旅行计划请求 DTO。
 *
 * @param conversationId AI 对话 ID（关联 AI 生成过程的上下文）
 * @param title 行程标题
 * @param departure 出发地（必填）
 * @param destination 目的地（必填）
 * @param days 行程天数（必填，1-7 天）
 * @param budget 预算金额
 * @param preferences 偏好标签列表
 * @param requirementJson 结构化旅行需求（JSON 格式）
 * @param tripPlanJson 完整行程计划（JSON 格式）
 * @param summary 行程摘要
 * @param coverUrl 封面图片 URL
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SaveTripRequest {

    private String conversationId;
    private String title;
    @NotBlank private String departure;
    @NotBlank private String destination;

    @NotNull @Min(1) @Max(7) private Integer days;

    private Integer budget;
    private List<String> preferences;
    private TravelRequirementDTO requirementJson;
    private TripPlanDTO tripPlanJson;
    private String summary;
    private String coverUrl;
}
