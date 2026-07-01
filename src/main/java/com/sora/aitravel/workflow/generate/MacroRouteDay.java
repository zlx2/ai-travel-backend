package com.sora.aitravel.workflow.generate;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MacroRouteDay {
    private Integer day;
    private String startAreaId;
    private List<String> focusAreaIds;
    private String endAreaId;
    private String stayAreaId;
    private String theme;
    private String reason;
}
