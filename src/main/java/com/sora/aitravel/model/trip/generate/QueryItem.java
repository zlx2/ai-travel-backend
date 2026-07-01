package com.sora.aitravel.model.trip.generate;

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

    public String type() {
        return type;
    }

    public String keyword() {
        return keyword;
    }

    public String around() {
        return around;
    }

    public String from() {
        return from;
    }

    public String to() {
        return to;
    }
}
