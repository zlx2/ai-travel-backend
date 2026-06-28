package com.sora.aitravel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("rental_vehicle_model")
public class RentalVehicleModel {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long groupId;
    private String groupCode;
    private String brand;
    private String series;
    private String seriesFullName;
    private Integer modelYear;
    private Integer launchYear;
    private String vehicleClass;
    private String bodyType;
    private String energyType;
    private String rawEnergyType;
    private String transmission;
    private Integer seats;
    private Integer guidePriceMinCent;
    private Integer guidePriceMaxCent;
    private String imageUrl;
    private String sourceUrl;
    private String summary;
    private String featureTags;
    private String confidence;
    private String note;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic private Integer deleted;
}
