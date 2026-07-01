package com.sora.aitravel.model;

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

    public Integer day() {
        return day;
    }

    public List<QueryItem> queries() {
        return queries;
    }
}
