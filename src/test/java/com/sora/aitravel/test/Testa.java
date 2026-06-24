package com.sora.aitravel.test;

import com.sora.aitravel.tools.ScenicTool;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class Testa {

    @Autowired private ChatModel chatModel;

    @Autowired private ScenicTool scenicTool;

    @Test
    void test() {

        ChatClient chatClient =
                ChatClient.builder(chatModel)
                        .defaultSystem(
                                "你是负责查询景点的模型 如果有人问你景点信息 请查询出来 取工具中的name字段返回成列表心形式[]"
                                        + "再将列表中的数据根据下面的分析并根据结构输出 "
                                        + "\"\"\"\n"
                                        + "你是一个旅游票价预测专家。\n"
                                        + "\n"
                                        + "你会根据用户输入的景点列表[]如果有多个景点 就要输出多个json判断门票价格范围。\n"
                                        + "\n"
                                        + "要求：\n"
                                        + "1. 只能输出 JSON，不要任何解释\n"
                                        + "2. 不要编造不存在的官方票价\n"
                                        + "3. 不确定要降低 confidence\n"
                                        + "4. 查出最低价格price_min和最高价格price_max\n"
                                        + "5. 给出游玩建议Suggestions\n"
                                        + "\n"
                                        + "输出格式：\n"
                                        + "{\n"
                                        + "  \"price_min\": 0,\n"
                                        + "  \"price_max\": 0,\n"
                                        + "  \"is_free\": true,\n"
                                        + "  \"ticket_types\": [],\n"
                                        + "  \"confidence\": 0.0,\n"
                                        + "  \"basis\": \"\"\n"
                                        + "  \"Suggestions\":\"\"\n"
                                        + "}\n"
                                        + "\"\"\"")
                        .build();

        ToolCallback[] toolCallbacks = ToolCallbacks.from(scenicTool);

        String result =
                chatClient.prompt().user("你好").toolCallbacks(toolCallbacks).call().content();

        System.out.println(result);
    }
}
