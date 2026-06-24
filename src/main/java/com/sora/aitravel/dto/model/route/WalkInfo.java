package com.sora.aitravel.dto.model.route;

import java.util.List;
import lombok.Data;

/** 步行信息 */
@Data
public class WalkInfo {
    private List<Step> steps;
}
