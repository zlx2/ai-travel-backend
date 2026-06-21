package com.sora.aitravel.dto.model;

import java.util.List;

/**
 * 目的地推荐结果 DTO。
 * <p>用于 AI 分析阶段，向用户推荐符合条件的旅行目的地。</p>
 *
 * @param name           推荐目的地名称
 * @param reason         推荐理由说明
 * @param tags           目的地标签列表（如 "海滨"、"文化"、"美食" 等）
 * @param recommendedDays 建议游玩天数
 */
public record DestinationSuggestionDTO(
        String name, String reason, List<String> tags, Integer recommendedDays) {}
