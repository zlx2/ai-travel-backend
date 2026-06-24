package com.sora.aitravel.dto.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 追问问题 DTO。
 *
 * <p>当 AI 分析用户输入的旅行需求不够完整时，用于向用户追问缺失信息。
 *
 * @param field 字段标识（对应 TravelRequirementDTO 中的字段名）
 * @param question 追问的问题描述
 * @param required 是否为必填信息（true-必填，false-可选）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionDTO {

    private String field;
    private String question;
    private Boolean required;
}
