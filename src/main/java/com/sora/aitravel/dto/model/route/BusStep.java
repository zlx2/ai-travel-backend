package com.sora.aitravel.dto.model.route;

import lombok.Data;

/** 公交站点信息 */
@Data
public class BusStep {
    private String instruction;
    private String roadName;
    private String stepDistance;
}
