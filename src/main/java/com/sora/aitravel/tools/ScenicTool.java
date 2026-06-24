package com.sora.aitravel.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class ScenicTool {
    @Tool(description = "根据关键词查询景点名称列表，例如：西昌景点、成都景点")
    public String Scenic(@ToolParam(description = "用户输入景点信息入成都景点,西安景点,青城山等") String keywords)
            throws Exception {

        // 1️⃣ 调用高德API
        String url =
                "https://restapi.amap.com/v3/place/text"
                        + "?key=2b1d87340145d770b92bc5dacb942c01"
                        + "&keywords="
                        + keywords;

        RestTemplate restTemplate = new RestTemplate();
        String json = restTemplate.getForObject(url, String.class);

        System.out.println("原始返回：" + json);

        // 2️⃣ 解析景点名称
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);

        List<String> nameList = new ArrayList<>();

        JsonNode pois = root.get("pois");
        if (pois != null && pois.isArray()) {
            for (JsonNode poi : pois) {
                JsonNode nameNode = poi.get("name");
                if (nameNode != null && !nameNode.isNull()) {
                    nameList.add(nameNode.asText());
                }
            }
        }
        System.out.println("查询成功！！！！！！！！！！！！");
        return nameList.toString();
    }
}
