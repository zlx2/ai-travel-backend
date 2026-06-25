package com.sora.aitravel.test;

import com.sora.aitravel.tools.ScenicTool;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class testnameList {

    @Autowired
    private ChatModel chatModel;


    @Autowired
    private ScenicTool scenicTool;

    @Test
    void test() throws Exception {

        // 第一步：查景点
        String scenicJson = scenicTool.Scenic("成都景点");

        // 第二步：生成票价预测
        String prompt = PRICE_PROMPT.formatted(scenicJson);

        String result = ChatClient.create(chatModel)
                .prompt(prompt)
                .call()
                .content();

        System.out.println(result);
    }

    private static final String PRICE_PROMPT = """
你是旅游票价预测专家。

根据景点列表生成门票预测。

要求：

1. 每个景点生成一个JSON对象
2. 只能输出JSON数组
3. 不允许输出解释文字
4. 不允许输出Markdown
5. 不允许输出推理过程
6. confidence范围0~1
7. 不允许编造确定价格
8. 不确定时降低confidence

格式：

[
  {
    "scenicName":"",
    "price_min":0,
    "price_max":0,
    "is_free":false,
    "ticket_types":[],
    "confidence":0.0,
    "basis":"",
    "suggestions":""
  }
]

景点列表：

%s
""";
}