package com.sora.aitravel.controller;

import cn.hutool.json.JSONUtil;
import com.sora.aitravel.common.result.R;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.request.TripGenerateRequest;
import com.sora.aitravel.tools.HotelTool;
import com.sora.aitravel.tools.WeatherTool;
import com.sora.aitravel.workflow.generate.GenerateWorkflowContext;
import com.sora.aitravel.workflow.generate.TripGenerateWorkflow;
import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
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
 *   <li>GET /api/ai/demo/workflow?destination=成都&days=3
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/demo")
@RequiredArgsConstructor
public class AiToolDemoController {

    private final ChatModel chatModel;
    private final WeatherTool weatherTool;
    private final HotelTool hotelTool;
    private final TripGenerateWorkflow tripGenerateWorkflow;
    private ChatClient chatClient;
    private ToolCallback[] toolCallbacks;

    // 初始化回调，生命周期管理，一次性执行，不会重复调用 ， 方法必须是void 返回类型
    @PostConstruct
    public void init() {
        // 构建 ChatClient，绑定系统提示词
        this.chatClient =
                ChatClient.builder(chatModel)
                        .defaultSystem(
                                """
                        你是一个专业的旅行助手，可以帮助用户查询天气和推荐酒店。
                        当前日期是 %s。当用户询问天气时，基于这个日期理解'今天'、'明天'等时间概念。
                        当用户询问天气时，请使用天气查询工具获取实时天气数据。
                        当用户询问酒店时，请使用酒店搜索工具获取酒店信息（包含品牌识别价格估算）。
                        请用友好的语气回复用户，并对工具返回的数据进行合理的总结和推荐。
                        如果用户同时问了天气和酒店，请分别调用对应的工具。
                        """.formatted(LocalDate.now()))
                .build();

        // 注册工具实例
        this.toolCallbacks = ToolCallbacks.from(weatherTool, hotelTool);

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

    /**
     * 工作流测试接口。
     *
     * <p>测试 TripGenerateWorkflow 是否正确集成了 WeatherTool 和 HotelTool。
     *
     * @param destination 目的地城市
     * @param days        行程天数（默认 3）
     * @param startDays   出发日期距今天数（默认 1，即明天出发）
     * @return 工作流执行结果（包含天气和酒店数据）
     */
    @GetMapping("/workflow")
    public R<Object> workflowTest(
            @RequestParam(defaultValue = "成都") String destination,
            @RequestParam(defaultValue = "3") Integer days,
            @RequestParam(defaultValue = "1") Integer startDays) {
        try {
            log.info("收到工作流测试请求：destination={}, days={}, startDays={}", destination, days, startDays);

            // 构建测试请求
            TravelRequirementDTO requirement =
                    new TravelRequirementDTO(
                            "北京", // departure
                            destination, // destination
                            "single", // routeMode
                            null, // routeStructure
                            null, // routeRegion
                            null, // routeCities
                            "train", // transportMode
                            null, // rentalIntent
                            null, // rentalRequirement
                            days, // days
                            5000, // budget
                            "total", // budgetType
                            2, // peopleCount
                            List.of("美食", "文化"), // preferences
                            "balanced", // pace
                            null, // avoidances
                            LocalDate.now().plusDays(startDays).toString() // travelDate
                            );

            TripGenerateRequest request = new TripGenerateRequest("test-conv", false, requirement, null, null);

            // 构建工作流上下文
            GenerateWorkflowContext context = new GenerateWorkflowContext();
            context.setUserId(1L);
            context.setRequest(request);

            // 执行工作流
            GenerateWorkflowContext result = tripGenerateWorkflow.execute(context);

            // 解析 JSON 字符串为对象，避免双重序列化
            Object weatherData = result.getWeatherForecast() != null && JSONUtil.isTypeJSON(result.getWeatherForecast())
                    ? JSONUtil.parse(result.getWeatherForecast())
                    : result.getWeatherForecast() != null ? result.getWeatherForecast() : "未获取到天气数据";
            Object hotelData = result.getHotelSearchResult() != null && JSONUtil.isTypeJSON(result.getHotelSearchResult())
                    ? JSONUtil.parse(result.getHotelSearchResult())
                    : result.getHotelSearchResult() != null ? result.getHotelSearchResult() : "未获取到酒店数据";

            // 返回关键数据
            return R.ok(Map.of(
                    "weatherForecast", weatherData,
                    "hotelSearchResult", hotelData,
                    "destination", destination,
                    "days", days,
                    "travelDate", LocalDate.now().plusDays(startDays).toString()));
        } catch (Exception e) {
            log.error("工作流测试失败", e);
            return R.fail(500, "工作流测试失败：" + e.getMessage());
        }
    }
}
