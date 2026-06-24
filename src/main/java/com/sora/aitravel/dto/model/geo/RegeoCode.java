package com.sora.aitravel.dto.model.geo;

import java.util.List;
import lombok.Data;

/** 逆地理编码结果 */
@Data
public class RegeoCode {
    /** 格式化地址 */
    private String formattedAddress;

    /** 地址组件 */
    private AddressComponent addressComponent;

    /** POI列表 */
    private List<RegeoPoi> pois;
}
