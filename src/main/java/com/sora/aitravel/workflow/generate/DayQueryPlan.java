package com.sora.aitravel.workflow.generate;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DayQueryPlan {
    private Integer day;
    private List<QueryItem> queries;

    Integer day() {
        return day;
    }

    List<QueryItem> queries() {
        return queries;
    }
}
