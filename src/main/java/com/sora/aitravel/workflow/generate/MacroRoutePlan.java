package com.sora.aitravel.workflow.generate;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MacroRoutePlan {
    private String id;
    private String routeShape;
    private List<MacroRouteDay> days;
    private List<String> warnings;
    private String reason;
}
