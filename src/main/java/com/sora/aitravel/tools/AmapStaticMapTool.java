package com.sora.aitravel.tools;

import com.sora.aitravel.dto.model.staticmap.StaticMapRequest;
import com.sora.aitravel.dto.model.staticmap.StaticMapResp;
import com.sora.aitravel.service.AmapApiService;
import jakarta.annotation.Resource;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
public class AmapStaticMapTool {

    @Resource
    private AmapApiService amapService;

    /**
     * 获取静态地图图片二进制/文本错误信息
     */
    @Tool(description = "生成高德静态地图图片，支持中心点、缩放、标记点、路线折线、文字标注、实时路况，返回图片二进制数据")
    public StaticMapResp getStaticMap(@ToolParam(
            description = "静态地图完整请求参数对象，可配置中心点、缩放、图片尺寸、高清倍数、标记Marker、文字Label、路线Path、实时路况；内部会自动填充高德key，无需手动传入",
            required = true
    ) StaticMapRequest request) {
        return amapService.staticMap(request);
    }
}
