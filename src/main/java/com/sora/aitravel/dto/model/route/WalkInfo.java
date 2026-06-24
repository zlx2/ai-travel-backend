package com.sora.aitravel.dto.model.route;

import lombok.Data;

import java.util.List;

/**
 * 步行信息
 */
@Data
public class WalkInfo {
    private List<Step> steps;
}
