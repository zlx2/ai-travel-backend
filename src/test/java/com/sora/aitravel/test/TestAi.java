package com.sora.aitravel.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

@SpringBootTest
public class TestAi {

    @Autowired
    private ChatModel chatModel;

    private ChatClient chatClient;

    @PostConstruct
    public void init() {
        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem("""
你是一个旅游票价预测专家。

你会根据用户输入的景点列表{input}如果有多个景点 就要输出多个json判断门票价格范围。

要求：
1. 只能输出 JSON，不要任何解释
2. 不要编造不存在的官方票价
3. 不确定要降低 confidence
4. 查出最低价格price_min和最高价格price_max
5. 给出游玩建议Suggestions

输出格式：
{
  "price_min": 0,
  "price_max": 0,
  "is_free": true,
  "ticket_types": [],
  "confidence": 0.0,
  "basis": ""
  "Suggestions":""
}
""")
                .build();
    }

    @Test
    void testExtractNames() throws Exception {

        // 1️⃣ 调用高德API
        String url = "https://restapi.amap.com/v3/place/text"
                + "?key=2b1d87340145d770b92bc5dacb942c01"
                + "&keywords=西昌景点";

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

        System.out.println("景点列表：" + nameList);

        // 3️⃣ ⭐把列表发给AI
        String input = String.join("、", nameList);

        String result = chatClient.prompt()
                .user("请根据以下景点列表估算门票价格：" + input)
                .call()
                .content();

        System.out.println("AI结果：");
        System.out.println(result);
    }
}