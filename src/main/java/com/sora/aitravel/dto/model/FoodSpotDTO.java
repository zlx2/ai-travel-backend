package com.sora.aitravel.dto.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 美食推荐候选。
 *
 * <p>表示行程生成前准备好的餐饮上下文，不等同于餐厅预订或实时排队信息。
 *
 * @param name 美食或餐饮区域名称
 * @param area 所在区域
 * @param specialty 特色
 * @param reason 推荐原因
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FoodSpotDTO {

    private String name;
    private String area;
    private String specialty;
    private String reason;
}
