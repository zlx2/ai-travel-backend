package com.sora.aitravel.workflow.generate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueryItem {
    private String type;
    private String keyword;
    private String city;
    private String around;
    private String from;
    private String to;
    private String purpose;

    String type() {
        return type;
    }

    String keyword() {
        return keyword;
    }

    String around() {
        return around;
    }

    String from() {
        return from;
    }

    String to() {
        return to;
    }
}
