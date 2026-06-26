package com.sora.aitravel.dto.response;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 目的地响应 DTO。
 *
 * @param id 目的地 ID
 * @param name 目的地名称
 * @param province 所属省份
 * @param city 所属城市
 * @param longitude 经度
 * @param latitude 纬度
 * @param coverUrl 封面图片 URL
 * @param description 目的地描述
 * @param tags 标签列表
 * @param heat 热度值
 * @param status 状态（0-禁用，1-启用）
 * @param createTime 创建时间
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DestinationResponse {

    private Long id;
    private String name;
    private String province;
    private String city;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String coverUrl;
    private String description;
    private List<String> tags;
    private Integer heat;
    private Integer status;
    private String createTime;
}
