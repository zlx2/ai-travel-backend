package com.sora.aitravel.workflow.generate;

import lombok.Data;
import lombok.NoArgsConstructor;

/** Daily route skeleton produced by macro route validation. */
@Data
@NoArgsConstructor
public class DaySkeleton {
    private Integer day;
    private String theme;
    private String targetArea;
    private String intensity;
    private String startAreaId;
    private String focusAreaId;
    private String endAreaId;
    private String stayAreaId;
    private AreaAnchorSnapshot startArea;
    private AreaAnchorSnapshot focusArea;
    private AreaAnchorSnapshot endArea;
    private AreaAnchorSnapshot stayArea;

    Integer day() {
        return day;
    }

    String theme() {
        return theme;
    }

    String targetArea() {
        return targetArea;
    }
}
