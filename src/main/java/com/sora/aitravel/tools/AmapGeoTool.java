package com.sora.aitravel.tools;

import com.sora.aitravel.dto.model.AmapApiResp;
import com.sora.aitravel.dto.model.geo.GeoCode;
import com.sora.aitravel.dto.model.geo.RegeoCode;
import com.sora.aitravel.service.AmapApiService;
import jakarta.annotation.Resource;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AmapGeoTool {

    @Resource
    private AmapApiService amapService;

    /**
     * 地理编码：地址转经纬度
     */
    @Tool(description = "地理编码转换，将中文结构化地址转换为经纬度坐标，例如传入'杭州市西湖区西湖'返回对应经纬度")
    public AmapApiResp<List<GeoCode>> geoCode(
            @ToolParam(description = "完整结构化地址，省市区街道门牌号，必填") String address,
            @ToolParam(description = "限定查询城市，城市名或adcode，提高匹配精度，可选", required = false) String city
    ) {
        return amapService.geoCode(address, city);
    }

    /**
     * 逆地理编码：经纬度转地址、周边POI、街道信息
     */
    @Tool(description = "逆地理编码转换，输入经纬度，反向解析出省市区街道、乡镇、周边地标、详细地址信息")
    public AmapApiResp<RegeoCode> reGeoCode(
            @ToolParam(description = "经纬度坐标，格式：经度,纬度，必填") String location,
            @ToolParam(description = "搜索半径，单位米，默认1000米", required = false) Integer radius,
            @ToolParam(description = "返回扩展信息：base只返回基础地址，all返回周边POI、道路、地标", required = false) String extensions
    ) {
        return amapService.reGeoCode(location, radius, extensions);
    }
}
