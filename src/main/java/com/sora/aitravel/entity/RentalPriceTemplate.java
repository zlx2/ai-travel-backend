package com.sora.aitravel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("rental_price_template")
public class RentalPriceTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String city;
    private String citycode;
    private String adcode;
    private Long vehicleGroupId;
    private Integer weekdayRentalFeeCent;
    private Integer weekendRentalFeeCent;
    private Integer baseServiceFeeCent;
    private Integer vehiclePrepareFeeCent;
    private Integer rentalDepositCent;
    private Integer violationDepositCent;
    private Integer depositFreeThresholdScore;
    private Integer oneWayBaseFeeCent;
    private Integer oneWayPerKmFeeCent;
    private BigDecimal oneWayDiscountRate;
    private Integer availableCount;
    private String sourcePlatform;
    private String sourceNote;
    private LocalDateTime sampledAt;
    private String confidence;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic private Integer deleted;
}
