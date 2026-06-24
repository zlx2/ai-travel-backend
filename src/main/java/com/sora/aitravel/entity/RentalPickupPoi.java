package com.sora.aitravel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("rental_pickup_poi")
public class RentalPickupPoi {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String source;
    private String sourcePoiId;
    private String poiName;
    private String poiType;
    private String poiTypecode;
    private String province;
    private String city;
    private String district;
    private String citycode;
    private String adcode;
    private String address;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String rawJson;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic private Integer deleted;
}
