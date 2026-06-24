package com.sora.aitravel.dto.model.geo;

import lombok.Data;

/**
 * 逆地理编码POI
 */
@Data
public class RegeoPoi {
    private String id;
    private String name;
    private String type;
    private String address;
    private String location;
    private String distance;
}
