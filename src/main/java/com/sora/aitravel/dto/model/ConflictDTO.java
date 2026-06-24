package com.sora.aitravel.dto.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 冲突检测结果 DTO。
 *
 * <p>用于 AI 分析阶段的冲突检测，标识旅行需求中存在矛盾或不合理的约束条件。
 *
 * @param type 冲突类型（如 budget-season 预算与季节冲突、time-distance 时间与距离冲突等）
 * @param message 冲突的详细描述信息
 * @param suggestion 解决冲突的建议或提示
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConflictDTO {

    private String type;
    private String message;
    private String suggestion;
}
