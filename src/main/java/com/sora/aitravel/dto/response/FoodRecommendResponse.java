package com.sora.aitravel.dto.response;

import com.sora.aitravel.common.enums.FoodSearchIntentTypeEnum;
import com.sora.aitravel.dto.model.FoodRestaurantItemDTO;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 美食推荐工具统一返回结构。
 *
 * <p>FoodTool 直接返回该 DTO，方便工作流读取 total、list、centerLocation 等结构化字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoodRecommendResponse {
    /** 本次查询是否成功。失败时 list 为空，message 给出原因。 */
    private Boolean success;

    /** 成功或失败提示信息。 */
    private String message;

    /** 数据来源，首版固定为 AMAP。 */
    private String source;

    /** 高德查询方式：AROUND 表示周边搜索，TEXT 表示关键字搜索。 */
    private String queryType;

    /** 本次美食查询的业务意图。 */
    private FoodSearchIntentTypeEnum intentType;

    /** 周边搜索中心点坐标，格式为“经度,纬度”；城市关键词搜索时可为空。 */
    private String centerLocation;

    /** 返回饭店数量。 */
    private Integer total;

    /** 饭店推荐列表。 */
    private List<FoodRestaurantItemDTO> list;

    public static FoodRecommendResponse fail(String message) {
        return new FoodRecommendResponse(false, message, "AMAP", null, null, null, 0, List.of());
    }
}
