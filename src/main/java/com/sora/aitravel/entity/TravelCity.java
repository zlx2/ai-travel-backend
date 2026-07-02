package com.sora.aitravel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.Data;

/** Normalized travel city entry used by AI trip generation. */
@Data
@TableName("travel_city")
public class TravelCity {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String cityName;
    private String province;
    private String amapCitycode;
    private String amapAdcode;
    private BigDecimal centerLng;
    private BigDecimal centerLat;
}
