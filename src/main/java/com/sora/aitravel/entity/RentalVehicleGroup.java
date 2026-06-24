package com.sora.aitravel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("rental_vehicle_group")
public class RentalVehicleGroup {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String groupCode;
    private String groupName;
    private String displayName;
    private String vehicleClass;
    private String bodyType;
    private String energyType;
    private String transmission;
    private Integer seatsMin;
    private Integer seatsMax;
    private String recommendedPeople;
    private String recommendedLuggage;
    private String travelTags;
    private String exampleModels;
    private String description;
    private String iconUrl;
    private Integer status;
    private Integer sortOrder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic private Integer deleted;
}
