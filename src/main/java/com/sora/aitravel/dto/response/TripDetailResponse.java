package com.sora.aitravel.dto.response;

import java.util.List;

/**
 * 旅行计划详情响应 DTO。
 *
 * @param id              行程 ID
 * @param conversationId  AI 对话 ID
 * @param title           行程标题
 * @param departure       出发地
 * @param destination     目的地
 * @param days            行程天数
 * @param budget          预算金额
 * @param preferences     偏好标签列表
 * @param requirementJson 结构化旅行需求（JSON 对象）
 * @param tripPlanJson    完整行程计划（JSON 对象）
 * @param summary         行程摘要
 * @param coverUrl        封面图片 URL
 * @param source          数据来源（0-AI 生成，1-用户手动创建，2-AI 生成后手动编辑）
 * @param status          行程状态（0-草稿，1-已完成）
 * @param createTime      创建时间
 * @param updateTime      更新时间
 */
public record TripDetailResponse(
        Long id,
        String conversationId,
        String title,
        String departure,
        String destination,
        Integer days,
        Integer budget,
        List<String> preferences,
        Object requirementJson,
        Object tripPlanJson,
        String summary,
        String coverUrl,
        Integer source,
        Integer status,
        String createTime,
        String updateTime) {}
