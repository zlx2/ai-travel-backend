package com.sora.aitravel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.Data;

/** Normalized route area; route skeletons should be based on these rows. */
@Data
@TableName("travel_area")
public class TravelArea {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long cityId;
    private String areaName;
    private BigDecimal centerLng;
    private BigDecimal centerLat;
    private BigDecimal radiusKm;
    private String areaType;
    private String ringLevel;
    private String direction;
    private String tagsJson;
    private Integer priorityScore;
    private BigDecimal suggestedDurationHours;
    private Integer exclusiveDay;
}
