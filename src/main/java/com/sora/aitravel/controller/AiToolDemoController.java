package com.sora.aitravel.controller;

import com.sora.aitravel.common.result.R;
import com.sora.aitravel.tools.HotelTool;
import com.sora.aitravel.tools.WeatherTool;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI Tool Calling 演示控制器。
 *
 * <p>演示 AI 如何通过 Tool Calling 机制自动调用天气查询和酒店搜索工具。 无需登录即可测试。
 *
 * <p>测试示例：
 *
 * <ul>
 *   <li>GET /api/ai/demo/tool?message=北京今天天气怎么样
 *   <li>GET /api/ai/demo/tool?message=我下周想去三亚玩，帮我推荐几家酒店，7月10日入住7月13日离店
 *   <li>GET /api/ai/demo/tool?message=帮我查一下成都的天气，再推荐几家酒店，住3晚从明天开始
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/demo")
public class AiToolDemoController {

    private final ChatModel chatModel;
    private final WeatherTool weatherTool;
    private final HotelTool hotelTool;
    private ChatClient chatClient;
    private ToolCallback[] toolCallbacks;

    public AiToolDemoController(ChatModel chatModel, WeatherTool weatherTool, HotelTool hotelTool) {
        this.chatModel = chatModel;
        this.weatherTool = weatherTool;
        this.hotelTool = hotelTool;
    }

    @PostConstruct
    public void init() {
        // 构建 ChatClient，绑定系统提示词
        this.chatClient =
                ChatClient.builder(chatModel)
                        .defaultSystem(
                                """
                        你是一个专业的旅行助手，可以帮助用户查询天气和推荐酒店。
                        当用户询问天气时，请使用天气查询工具获取实时天气数据。
                        当用户询问酒店时，请使用酒店搜索工具获取酒店信息（包含品牌识别价格估算）。
                        请用友好的语气回复用户，并对工具返回的数据进行合理的总结和推荐。
                        如果用户同时问了天气和酒店，请分别调用对应的工具。
                        """)
                        .build();

        // 注册工具实例
        this.toolCallbacks = ToolCallbacks.from(weatherTool, hotelTool);

        log.info("AI Tool Calling Demo 初始化完成，已注册工具：WeatherTool, HotelTool");
    }

    /**
     * AI Tool Calling 演示接口。
     *
     * <p>AI 会根据用户消息自动判断是否需要调用天气/酒店工具， 并将工具返回的数据整合成自然语言回复。
     *
     * @param message 用户消息
     * @return AI 的回复内容
     */
    @GetMapping("/tool")
    public R<String> toolCallingDemo(@RequestParam String message) {
        try {
            log.info("收到 Tool Calling 演示请求：{}", message);

            // 核心调用：AI 会自动判断并调用合适的工具
            String reply =
                    chatClient.prompt().user(message).toolCallbacks(toolCallbacks).call().content();

            log.info("AI Tool Calling 回复：{}", reply);
            return R.ok(reply);
        } catch (Exception e) {
            log.error("AI Tool Calling 演示失败", e);
            return R.fail(500, "AI 调用失败：" + e.getMessage());
        }
    }
}
