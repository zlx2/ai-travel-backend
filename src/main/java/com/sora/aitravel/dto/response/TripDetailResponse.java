package com.sora.aitravel.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 旅行计划详情响应 DTO。
 *
 * @param id 行程 ID
 * @param conversationId AI 对话 ID
 * @param title 行程标题
 * @param departure 出发地
 * @param destination 目的地
 * @param days 行程天数
 * @param budget 预算金额
 * @param preferences 偏好标签列表
 * @param requirementJson 结构化旅行需求（JSON 对象）
 * @param tripPlanJson 完整行程计划（JSON 对象）
 * @param summary 行程摘要
 * @param coverUrl 封面图片 URL
 * @param source 数据来源（0-AI 生成，1-用户手动创建，2-AI 生成后手动编辑）
 * @param status 行程状态（0-草稿，1-已完成）
 * @param createTime 创建时间
 * @param updateTime 更新时间
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TripDetailResponse {

    private Long id;
    private String conversationId;
    private String title;
    private String departure;
    private String destination;
    private Integer days;
    private Integer budget;
    private List<String> preferences;
    private Object requirementJson;
    private Object tripPlanJson;
    private String summary;
    private String coverUrl;
    private Integer source;
    private Integer status;
    private String createTime;
    private String updateTime;
}
