package com.sora.aitravel.test;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.AgentTool;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.sora.aitravel.tools.ScenicTool;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

@SpringBootTest
public class testdemo2 {
    @Autowired
    private ChatModel chatModel;

    @Autowired
    private ScenicTool scenicTool;
    private static final String s= """
你是旅游票价预测专家。
根据tool返回列表里面的景点
输出格式：
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

规则：

1. 只能输出JSON数组。
2. 禁止输出解释。
3. 禁止输出Markdown。
4. 禁止输出推理过程。
5. 禁止输出Observation。
6. 禁止输出任何额外文字。
""";


    @Test
    void test() throws GraphRunnerException {


//        ReactAgent reactAgent=ReactAgent.builder()
//                .name("test")
//                .model(chatModel)
//                .methodTools(scenicTool)
//                .description("你是专门查询景点详细信息的返回列表[]里面只存景点名字  不要回多余信息只返回列表")
//                .build();
////        String result = String.valueOf(reactAgent.call("成都有哪些景点"));
//        // 创建主Agent，将子Agent作为工具
//        ReactAgent blogAgent = ReactAgent.builder()
//                .name("blog_agent")
//                .model(chatModel)
//                .tools(AgentTool.getFunctionToolCallback(reactAgent))
//                .instruction(s)
//                .build();
        ReactAgent agent = ReactAgent.builder()
                .name("ticket-agent")
                .model(chatModel)
                .methodTools(scenicTool)
                .instruction("""
你是旅游票价预测专家。
必须：
1. 调用工具获取景点
2. 不允许编造
3. 一个景点一个JSON对象
4. 只输出JSON数组包含下面字段
5.只允许json里面存在这些字段信息不要乱加符号
输出格式：
    "scenicName":"",
    "price_min":0,
    "price_max":0,
    "is_free":false,
    "ticket_types":[],
    "confidence":0.0,
    "basis":"",
    "suggestions":""
""")
                .build();

        Optional<OverAllState> s = agent.invoke("成都有哪些景点");

        OverAllState overAllState = s.get();
        System.out.println("-------------");
        System.out.println(s);
        System.out.println("-------------");
        System.out.println(overAllState);
    }
}
