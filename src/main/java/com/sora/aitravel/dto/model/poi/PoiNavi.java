package com.sora.aitravel.dto.model.poi;

import lombok.Data;

/** 高德 POI 导航扩展字段。 */
@Data
public class PoiNavi {
    private String naviPoiid;
    private String entrLocation;
    private String exitLocation;
    private String gridcode;
}
