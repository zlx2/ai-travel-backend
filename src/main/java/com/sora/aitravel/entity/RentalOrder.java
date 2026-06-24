package com.sora.aitravel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("rental_order")
public class RentalOrder {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;
    private Long userId;
    private Long tripId;
    private Long pickupPoiId;
    private String pickupMode;
    private Long returnPoiId;
    private String returnMode;
    private String deliveryAddress;
    private String returnAddress;
    private Integer deliveryFeeCent;
    private String pickupPoiSnapshot;
    private String returnPoiSnapshot;
    private Long vehicleGroupId;
    private Long assignedModelId;
    private LocalDateTime pickupTime;
    private LocalDateTime returnTime;
    private BigDecimal rentalDays;
    private Integer isOneWay;
    private Integer rentalFeeCent;
    private Integer baseServiceFeeCent;
    private Integer vehiclePrepareFeeCent;
    private Integer oneWayBaseFeeCent;
    private Integer oneWayDiscountCent;
    private Integer oneWayFinalFeeCent;
    private Integer rentalDepositCent;
    private Integer violationDepositCent;
    private Integer depositFreeThresholdScore;
    private Integer totalPriceCent;
    private Long priceTemplateId;
    private String priceSnapshot;
    private String contactName;
    private String contactPhone;
    private String orderStatus;
    private String paymentStatus;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic private Integer deleted;
}
