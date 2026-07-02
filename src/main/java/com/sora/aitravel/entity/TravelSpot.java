package com.sora.aitravel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.Data;

/** Normalized high-value scenic spot; AI generated spots must reference this table. */
@Data
@TableName("travel_spot")
public class TravelSpot {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long cityId;
    private Long areaId;
    private String source;
    private String sourcePoiId;
    private String spotName;
    private String aliasJson;
    private BigDecimal lng;
    private BigDecimal lat;
    private String address;
    private String spotType;
    private String tagsJson;
    private String valueLevel;
    private Integer qualityScore;
    private Integer popularityScore;
    private Integer recommendedDurationMin;
    private String bestTimeJson;
    private String openTimeText;
    private String ticketText;
    private String physicalLevel;
    private Integer rainFriendly;
    private Integer nightFriendly;
    private Integer familyFriendly;
}
