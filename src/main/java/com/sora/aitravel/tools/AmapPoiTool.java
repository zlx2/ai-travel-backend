package com.sora.aitravel.tools;

import com.sora.aitravel.dto.model.AmapApiResp;
import com.sora.aitravel.dto.model.poi.Poi;
import com.sora.aitravel.service.AmapApiService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AmapPoiTool {

    private final AmapApiService amapService;

    /** POI文本搜索：根据关键词/分类搜索地点，适用于景点、酒店、餐厅、商铺查询 */
    @Tool(description = "高德POI文本搜索，根据关键词、POI类型、城市区域搜索地点，可用于查询景点、酒店、餐饮、商铺等场所信息")
    public AmapApiResp<List<Poi>> searchPoiText(
            @ToolParam(description = "搜索关键词，如：西湖、海底捞、酒店；keywords和types至少传一个", required = false)
                    String keywords,
            @ToolParam(description = "POI分类编码，多个用|分隔，如060101表示风景名胜", required = false) String types,
            @ToolParam(description = "搜索城市/区划，城市名或adcode，如杭州市/330100", required = false)
                    String region,
            @ToolParam(description = "是否仅限制当前城市搜索，true仅当前城市，false全国搜索", required = false)
                    Boolean cityLimit) {
        return amapService.searchPoiText(keywords, types, region, cityLimit);
    }

    /** POI周边搜索：根据中心点经纬度搜索周边场所 */
    @Tool(description = "高德POI周边搜索，传入中心点经纬度，搜索指定半径范围内的酒店、餐厅、景点等地点")
    public AmapApiResp<List<Poi>> searchPoiAround(
            @ToolParam(description = "中心点经纬度，格式：经度,纬度 例如 120.15,30.28") String location,
            @ToolParam(description = "搜索关键词，可选，缩小搜索范围", required = false) String keywords,
            @ToolParam(description = "POI分类编码，多个用|分隔", required = false) String types,
            @ToolParam(description = "搜索半径，单位米，最大5000米", required = false) Integer radius) {
        return amapService.searchPoiAround(location, keywords, types, radius);
    }
}
