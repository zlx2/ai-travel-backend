package com.sora.aitravel.dto.model.poi;

import lombok.Data;

/** 高德 POI business 扩展字段。 */
@Data
public class PoiBusiness {
    private String businessArea;
    private String opentimeToday;
    private String opentimeWeek;
    private String tel;
    private String tag;
    private String rating;
    private String cost;
    private String alias;
    private String keytag;
    private String rectag;
}
