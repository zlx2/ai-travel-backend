package com.sora.aitravel.workflowtest;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategyFactoryBuilder;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Spring AI Alibaba Graph 工作流 Demo —— 第二个节点接入 AI。
 *
 * <pre>
 * 链路:
 *   START → input-parse → spot-search(AI) → condition-check ─┬─→ format ──┬─→ result-merge → END
 *                                                              └─→ fallback ─┘
 * </pre>
 */
@SpringBootTest
class ScenicWorkflowTest {

    static final String INPUT   = "input";
    static final String DEST    = "dest";
    static final String SPOTS   = "spots";
    static final String AI_JSON = "aiJson";
    static final String STATUS  = "status";
    static final String RESULT  = "result";

    @Autowired
    private ChatModel chatModel;

    private ChatClient chatClient;
    private CompiledGraph graph;

    @PostConstruct
    void initChatClient() {
        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem(           """
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
""")
                .build();
    }

    @BeforeEach
    void setUp() throws GraphStateException {
        this.graph = compile();
    }

    // ==================== 测试用例 ====================

    @Test
    @DisplayName("查看工作流结构")
    void printGraph() throws GraphStateException {
        StateGraph sg = buildStateGraph();
        System.out.println(sg);
        // StateGraph 的 toString() 会输出节点和边的 ASCII 图
    }

    @Test
    @DisplayName("输入杭州 → AI 推荐景点 → format 分支")
    void shouldCallAiAndFormat() {
        OverAllState state = run("帮我推荐杭州的景点");

        System.out.println("=== AI 推荐结果 ===");
        System.out.println("-------"+state.value(RESULT));
        System.out.println("目的地: " + state.value(DEST).orElse(""));
        System.out.println("状态: " + state.value(STATUS).orElse(""));
        System.out.println(state.value(RESULT).orElse(""));
    }

    @Test
    @DisplayName("输入火星 → AI 无法识别 → fallback 分支")
    void shouldFallback() {
        OverAllState state = run("我想去火星旅游");

        System.out.println("=== 兜底分支 ===");
        System.out.println("状态: " + state.value(STATUS).orElse(""));
        System.out.println(state.value(RESULT).orElse(""));
    }

    // ==================== 执行入口 ====================

    private OverAllState run(String userInput) {
        Map<String, Object> initialState = new LinkedHashMap<>();
        initialState.put(INPUT, userInput);
        return graph.invoke(initialState).orElseThrow();
    }

    // ==================== 编译 StateGraph ====================

    CompiledGraph compile() throws GraphStateException {
        return buildStateGraph().compile();
    }

    /** 构建 StateGraph（compile 前），方便 inspect 结构 */
    StateGraph buildStateGraph() throws GraphStateException {
        StateGraph graph = new StateGraph(
                "scenic-workflow",
                new KeyStrategyFactoryBuilder()
                        .addStrategy(INPUT,  new ReplaceStrategy())
                        .addStrategy(DEST,   new ReplaceStrategy())
                        .addStrategy(SPOTS,  new ReplaceStrategy())
                        .addStrategy(AI_JSON, new ReplaceStrategy())
                        .addStrategy(STATUS, new ReplaceStrategy())
                        .addStrategy(RESULT, new ReplaceStrategy())
                        .build());

        // ★ spot-search 换成 AI 节点
        graph.addNode("input-parse",     node(this::parseInput));
        graph.addNode("spot-search",     node(this::searchSpotsWithAi));
        graph.addNode("parallelNode",    node(s -> {}));  // 空节点：纯透传 state
        graph.addNode("condition-check", node(this::checkCondition));
        graph.addNode("format",          node(this::formatOutput));
        graph.addNode("fallback",        node(this::fallbackSuggest));
        graph.addNode("result-merge",    node(this::mergeResult));

        graph.addEdge(StateGraph.START, "input-parse");
        graph.addEdge("input-parse",    "spot-search");
        graph.addEdge("spot-search",    "parallelNode");
        graph.addEdge("parallelNode",    "condition-check");

        graph.addConditionalEdges(
                "condition-check",
                AsyncEdgeAction.edge_async(s -> s.value(STATUS, "NO_RESULT")),
                Map.of("READY", "format", "NO_RESULT", "fallback"));

        graph.addEdge("format",       "result-merge");
        graph.addEdge("fallback",     "result-merge");
        graph.addEdge("result-merge", StateGraph.END);
        return graph;
    }

    // ==================== 节点实现 ====================

    /** 节点1：提取目的地 */
    void parseInput(Map<String, Object> s) {
        String input = (String) s.getOrDefault(INPUT, "");
        for (String c : new String[]{"杭州","成都","重庆","西安","厦门","北京","上海","南京","丽江"})
            if (input.contains(c)) { s.put(DEST, c); return; }
        s.put(DEST, input);
    }

    /** ★ 节点2：调用 AI 获取景点推荐 */
    void searchSpotsWithAi(Map<String, Object> s) {
        String dest = (String) s.get(DEST);
        if (dest == null || dest.isBlank()) {
            s.put(SPOTS, List.of());
            return;
        }
        try {
            // 调 AI
            String aiResult = chatClient.prompt()
                    .user("请推荐" + dest + "最值得去的5个景点")
                    .call()
                    .content();

            System.out.println("AI 原始返回: " + aiResult);

            // ★ 把 AI 完整返回存入 state，下游节点都能取到
            System.out.println(s.put(AI_JSON, aiResult));
            // 解析 JSON 里的 scenicName 给条件判断用
            List<String> names = extractSpotNames(aiResult);
            System.out.println("names" +names);
            s.put(SPOTS, names.isEmpty() ? List.of() : names);
        } catch (Exception e) {
            System.out.println("AI 调用失败: " + e.getMessage());
            s.put(SPOTS, List.of());
        }
    }

    /** 简单 JSON 解析：从 AI 返回中抠出 name 字段 */
    private List<String> extractSpotNames(String aiResult) {
        List<String> names = new ArrayList<>();
        // 找到 spots 数组里的每个 "name": "xxx"
        String[] parts = aiResult.split("\"scenicName\"\\s*:\\s*\"");
        for (int i = 1; i < parts.length; i++) {
            int end = parts[i].indexOf('"');
            if (end > 0) names.add(parts[i].substring(0, end));
        }
        return names;
    }

    void checkCondition(Map<String, Object> s) {
        List<?> spots = (List<?>) s.getOrDefault(SPOTS, List.of());
        s.put(STATUS, spots.isEmpty() ? "NO_RESULT" : "READY");
    }

    void formatOutput(Map<String, Object> s) {
        // ★ 直接把 AI 原始 JSON 作为最终输出
        String aiJson = (String) s.get(AI_JSON);

        if (aiJson != null && !aiJson.isBlank()) {
            s.put(RESULT, aiJson);
        } else {
            s.put(RESULT, "[]");
        }
    }

    void fallbackSuggest(Map<String, Object> s) {
        s.put(RESULT, "AI 暂未识别「" + s.get(DEST) + "」的景点信息。"
                + "\n建议尝试：杭州、成都、重庆、西安、厦门、北京。");
    }

    void mergeResult(Map<String, Object> s) {
        if (s.get(RESULT) == null || s.get(RESULT).toString().isBlank())
            s.put(RESULT, "未能生成推荐结果。");
    }

    // ==================== 工具 ====================

    AsyncNodeAction node(Consumer<Map<String, Object>> fn) {
        return AsyncNodeAction.node_async(state -> {
            Map<String, Object> mutable = new LinkedHashMap<>(state.data());
            System.out.println("--------------"+state);
            fn.accept(mutable);
            return mutable;
        });
    }
}
