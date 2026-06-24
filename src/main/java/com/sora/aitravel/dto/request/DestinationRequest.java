package com.sora.aitravel.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 目的地操作请求 DTO（管理后台创建/更新目的地用）。
 *
 * @param name 目的地名称（必填）
 * @param province 所属省份
 * @param city 所属城市
 * @param longitude 经度
 * @param latitude 纬度
 * @param coverUrl 封面图片 URL
 * @param description 目的地描述
 * @param tags 标签列表
 * @param heat 热度值（影响排序权重）
 * @param status 状态（0-禁用，1-启用）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DestinationRequest {

    @NotBlank private String name;
    private String province;
    private String city;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String coverUrl;
    private String description;
    private List<String> tags;
    private Integer heat;
    private Integer status;
}
