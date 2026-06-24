package com.sora.aitravel.dto.model.geo;

import lombok.Data;

/**
 * 地址组件
 */
@Data
public class AddressComponent {
    private String country;
    private String province;
    private String city;
    private String citycode;
    private String district;
    private String adcode;
    private String township;
}
