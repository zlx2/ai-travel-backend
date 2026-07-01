package com.sora.aitravel.ai;

import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.sora.aitravel.config.AmapProperties;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScenicTool {

    private final AmapProperties amapProperties;

    @Tool(description = "根据关键词查询景点名称列表，例如：西昌景点、成都景点")
    public String Scenic(@ToolParam(description = "用户输入景点信息入成都景点,西安景点,青城山等") String keywords) {
        String url = endpoint("/v3/place/text");
        log.info("ScenicTool 查询景点, keywords={}", keywords);

        String json =
                HttpRequest.get(url)
                        .form("key", amapProperties.getApiKey())
                        .form("keywords", keywords)
                        .timeout(Math.toIntExact(amapProperties.getTimeout().toMillis()))
                        .execute()
                        .body();

        log.debug("高德返回: {}", json);

        JSONObject root = JSONUtil.parseObj(json);
        List<String> nameList = new ArrayList<>();

        JSONArray pois = root.getJSONArray("pois");
        if (pois != null) {
            for (Object item : pois) {
                JSONObject poi = (JSONObject) item;
                String name = poi.getStr("name");
                if (name != null) {
                    nameList.add(name);
                }
            }
        }
        log.info("ScenicTool 查询成功, 结果数={}", nameList.size());
        return nameList.toString();
    }

    private String endpoint(String path) {
        String baseUrl = amapProperties.getBaseUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + path;
    }
}
