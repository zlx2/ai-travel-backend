package com.sora.aitravel.workflow.analyze;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategyFactoryBuilder;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sora.aitravel.dto.model.ConflictDTO;
import com.sora.aitravel.dto.model.DestinationSuggestionDTO;
import com.sora.aitravel.dto.model.QuestionDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.response.TripAnalyzeResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;

@Slf4j
@EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+")
class TravelAnalyzeGraphDemoTest {

    private static final String INPUT_REQUEST = "inputRequest";
    private static final String RAW_INPUT = "rawInput";
    private static final String CLEAN_INPUT = "cleanInput";
    private static final String DRAFT_REQUIREMENT = "draftRequirement";
    private static final String REQUIREMENT = "requirement";
    private static final String STATUS = "status";
    private static final String QUESTIONS = "questions";
    private static final String DESTINATION_SUGGESTIONS = "destinationSuggestions";
    private static final String CONFLICTS = "conflicts";
    private static final String RESULT = "result";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ChatModel chatModel = buildDeepSeekChatModel();

    @Test
    void 完整输入应该返回READY分析结果() throws Exception {
        CompiledGraph graph = buildAnalyzeGraph();
        AnalyzeInput input =
                new AnalyzeInput(
                        "conv-ready",
                        "我想从上海去成都玩4天，预算5000，两个人，喜欢美食和自然风光，不想太累",
                        null,
                        List.of(),
                        null);

        List<NodeOutput> outputs = graph.stream(Map.of(INPUT_REQUEST, input)).collectList().block();

        assertThat(outputs).isNotNull();
        assertThat(nodeNames(outputs))
                .containsSubsequence(
                        StateGraph.START,
                        "接收原始输入",
                        "整理输入",
                        "AI抽取需求",
                        "标准化需求",
                        "完整性检查",
                        "冲突检查",
                        "组装分析结果",
                        StateGraph.END);

        TripAnalyzeResponse result = result(outputs);
        assertThat(result.getStatus()).isIn("READY", "CONFLICT");
        assertThat(result.getRequirement()).isNotNull();
        assertThat(result.getRequirement().getDeparture()).isNotBlank();
        assertThat(result.getRequirement().getDestination()).isNotBlank();
        assertThat(result.getRequirement().getDays()).isNotNull();
    }

    @Test
    void 缺少关键信息应该返回追问问题() throws Exception {
        CompiledGraph graph = buildAnalyzeGraph();
        AnalyzeInput input =
                new AnalyzeInput("conv-more-info", "我想出去玩几天，喜欢美食", null, List.of(), null);

        List<NodeOutput> outputs = graph.stream(Map.of(INPUT_REQUEST, input)).collectList().block();

        assertThat(outputs).isNotNull();
        assertThat(nodeNames(outputs))
                .containsSubsequence(
                        "接收原始输入", "整理输入", "AI抽取需求", "标准化需求", "完整性检查", "组装分析结果", StateGraph.END);
        assertThat(nodeNames(outputs)).doesNotContain("目的地推荐", "冲突检查");

        TripAnalyzeResponse result = result(outputs);
        assertThat(result.getStatus()).isEqualTo("NEED_MORE_INFO");
        assertThat(result.getQuestions()).isNotEmpty();
    }

    @Test
    void 缺少目的地但有偏好应该返回目的地候选() throws Exception {
        CompiledGraph graph = buildAnalyzeGraph();
        AnalyzeInput input =
                new AnalyzeInput(
                        "conv-destination-choice", "我从上海出发玩4天，喜欢美食和自然风光", null, List.of(), null);

        List<NodeOutput> outputs = graph.stream(Map.of(INPUT_REQUEST, input)).collectList().block();

        assertThat(outputs).isNotNull();
        assertThat(nodeNames(outputs))
                .containsSubsequence("完整性检查", "目的地推荐", "组装分析结果", StateGraph.END);
        assertThat(nodeNames(outputs)).doesNotContain("冲突检查");

        TripAnalyzeResponse result = result(outputs);
        assertThat(result.getStatus()).isEqualTo("NEED_DESTINATION_CHOICE");
        assertThat(result.getDestinationSuggestions()).hasSize(3);
        assertThat(destinationNames(result.getDestinationSuggestions()))
                .allSatisfy(name -> assertThat(name).isNotBlank());
    }

    @Test
    void 明显不合理的需求应该返回冲突结果() throws Exception {
        CompiledGraph graph = buildAnalyzeGraph();
        AnalyzeInput input =
                new AnalyzeInput(
                        "conv-conflict", "我想从上海去成都玩8天，预算500，4个人，喜欢美食和自然风光", null, List.of(), null);

        List<NodeOutput> outputs = graph.stream(Map.of(INPUT_REQUEST, input)).collectList().block();

        assertThat(outputs).isNotNull();
        assertThat(nodeNames(outputs))
                .containsSubsequence("标准化需求", "完整性检查", "冲突检查", "组装分析结果", StateGraph.END);

        TripAnalyzeResponse result = result(outputs);
        assertThat(result.getStatus()).isEqualTo("CONFLICT");
        assertThat(result.getConflicts()).isNotEmpty();
    }

    private CompiledGraph buildAnalyzeGraph() throws Exception {
        StateGraph graph =
                new StateGraph(
                        "travel-analyze-v1-real-llm-demo",
                        new KeyStrategyFactoryBuilder()
                                .addStrategy(INPUT_REQUEST, new ReplaceStrategy())
                                .addStrategy(RAW_INPUT, new ReplaceStrategy())
                                .addStrategy(CLEAN_INPUT, new ReplaceStrategy())
                                .addStrategy(DRAFT_REQUIREMENT, new ReplaceStrategy())
                                .addStrategy(REQUIREMENT, new ReplaceStrategy())
                                .addStrategy(STATUS, new ReplaceStrategy())
                                .addStrategy(QUESTIONS, new ReplaceStrategy())
                                .addStrategy(DESTINATION_SUGGESTIONS, new ReplaceStrategy())
                                .addStrategy(CONFLICTS, new ReplaceStrategy())
                                .addStrategy(RESULT, new ReplaceStrategy())
                                .build());

        graph.addNode("接收原始输入", AsyncNodeAction.node_async(this::receiveRawInput));
        graph.addNode("整理输入", AsyncNodeAction.node_async(this::normalizeInput));
        graph.addNode("AI抽取需求", AsyncNodeAction.node_async(this::extractRequirementWithLlm));
        graph.addNode("标准化需求", AsyncNodeAction.node_async(this::standardizeRequirement));
        graph.addNode("完整性检查", AsyncNodeAction.node_async(this::checkCompleteness));
        graph.addNode("目的地推荐", AsyncNodeAction.node_async(this::recommendDestinationsWithLlm));
        graph.addNode("冲突检查", AsyncNodeAction.node_async(this::checkConflictsWithLlm));
        graph.addNode("组装分析结果", AsyncNodeAction.node_async(this::assembleResult));

        graph.addEdge(StateGraph.START, "接收原始输入");
        graph.addEdge("接收原始输入", "整理输入");
        graph.addEdge("整理输入", "AI抽取需求");
        graph.addEdge("AI抽取需求", "标准化需求");
        graph.addEdge("标准化需求", "完整性检查");
        graph.addConditionalEdges(
                "完整性检查",
                AsyncEdgeAction.edge_async(state -> state.value(STATUS, "NEED_MORE_INFO")),
                Map.of(
                        "NEED_MORE_INFO",
                        "组装分析结果",
                        "NEED_DESTINATION_CHOICE",
                        "目的地推荐",
                        "READY",
                        "冲突检查"));
        graph.addEdge("目的地推荐", "组装分析结果");
        graph.addConditionalEdges(
                "冲突检查",
                AsyncEdgeAction.edge_async(state -> state.value(STATUS, "READY")),
                Map.of("CONFLICT", "组装分析结果", "READY", "组装分析结果"));
        graph.addEdge("组装分析结果", StateGraph.END);

        return graph.compile();
    }

    private Map<String, Object> receiveRawInput(OverAllState state) {
        AnalyzeInput input = state.value(INPUT_REQUEST, AnalyzeInput.class).orElseThrow();
        log.info("节点[接收原始输入]：接收用户文字/表单输入，conversationId={}", input.getConversationId());
        return Map.of(RAW_INPUT, input);
    }

    private Map<String, Object> normalizeInput(OverAllState state) {
        AnalyzeInput input = state.value(RAW_INPUT, AnalyzeInput.class).orElseThrow();
        log.info("节点[整理输入]：合并 userInput、formInput、extraAnswers 和 selectedDestination。");

        StringBuilder cleanInput = new StringBuilder();
        if (input.getUserInput() != null && !input.getUserInput().isBlank()) {
            cleanInput.append(input.getUserInput().trim());
        }
        if (input.getFormInput() != null) {
            append(cleanInput, "出发地：" + input.getFormInput().getDeparture());
            append(cleanInput, "目的地：" + input.getFormInput().getDestination());
            append(cleanInput, "天数：" + input.getFormInput().getDays());
            append(cleanInput, "预算：" + input.getFormInput().getBudget());
            append(cleanInput, "人数：" + input.getFormInput().getPeopleCount());
            append(cleanInput, "偏好：" + input.getFormInput().getPreferences());
        }
        if (input.getExtraAnswers() != null && !input.getExtraAnswers().isEmpty()) {
            append(cleanInput, "补充回答：" + String.join("；", input.getExtraAnswers()));
        }
        if (input.getSelectedDestination() != null && !input.getSelectedDestination().isBlank()) {
            append(cleanInput, "用户已选择目的地：" + input.getSelectedDestination());
        }
        return Map.of(CLEAN_INPUT, cleanInput.toString().replaceAll("\\s+", " "));
    }

    private Map<String, Object> extractRequirementWithLlm(OverAllState state) {
        String cleanInput = state.value(CLEAN_INPUT, "");
        log.info("节点[AI抽取需求]：真实调用 ChatModel，从干净输入里抽取结构化旅行需求字段。");

        String json =
                callLlmForJson(
                        """
                        你是旅行需求分析器。请只输出 JSON，不要 Markdown，不要解释。
                        从用户输入中抽取旅行需求。缺失字段用 null，数组字段缺失用 []。

                        JSON 字段必须是：
                        {
                          "departure": null,
                          "destination": null,
                          "days": null,
                          "budget": null,
                          "budgetType": "TOTAL",
                          "peopleCount": null,
                          "preferences": [],
                          "pace": "NORMAL",
                          "avoidances": [],
                          "travelDate": null
                        }

                        约束：
                        - days、budget、peopleCount 必须是数字或 null。
                        - pace 只能是 LIGHT、NORMAL、INTENSIVE 之一。
                        - budgetType 默认 TOTAL。

                        用户输入：
                        %s
                        """
                                .formatted(cleanInput));
        return Map.of(DRAFT_REQUIREMENT, readMap(json));
    }

    private Map<String, Object> standardizeRequirement(OverAllState state) {
        Map<String, Object> draft = state.value(DRAFT_REQUIREMENT, Map.class).orElseThrow();
        log.info("节点[标准化需求]：用规则稳定 AI 抽取结果，补默认值并统一枚举。");

        String destination = stringValue(draft.get("destination"));
        TravelRequirementDTO requirement =
                new TravelRequirementDTO(
                        stringValue(draft.get("departure")),
                        destination,
                        "DESTINATION_CITY_TRIP",
                        "SINGLE_CITY",
                        null,
                        destination == null ? List.of() : List.of(destination),
                        "PUBLIC_TRANSIT",
                        "NO_RENTAL",
                        null,
                        intValue(draft.get("days")),
                        intValue(draft.get("budget")),
                        defaultString(draft.get("budgetType"), "TOTAL"),
                        defaultInt(draft.get("peopleCount"), 1),
                        stringList(draft.get("preferences")),
                        normalizePace(draft.get("pace")),
                        stringList(draft.get("avoidances")),
                        stringValue(draft.get("travelDate")));
        return Map.of(REQUIREMENT, requirement);
    }

    private Map<String, Object> checkCompleteness(OverAllState state) {
        TravelRequirementDTO requirement =
                state.value(REQUIREMENT, TravelRequirementDTO.class).orElseThrow();
        String cleanInput = state.value(CLEAN_INPUT, "");
        log.info("节点[完整性检查]：检查出发地、目的地、天数是否满足 Analyze 最小必填。");

        List<String> missingFields = new ArrayList<>();
        if (isBlank(requirement.getDeparture())) {
            missingFields.add("departure");
        }
        if (requirement.getDays() == null) {
            missingFields.add("days");
        }
        if (!missingFields.isEmpty()) {
            return Map.of(
                    STATUS,
                    "NEED_MORE_INFO",
                    QUESTIONS,
                    buildQuestionsWithLlm(cleanInput, requirement, missingFields));
        }
        if (isBlank(requirement.getDestination())) {
            if (requirement.getPreferences() != null && !requirement.getPreferences().isEmpty()) {
                return Map.of(STATUS, "NEED_DESTINATION_CHOICE", QUESTIONS, List.of());
            }
            return Map.of(
                    STATUS,
                    "NEED_MORE_INFO",
                    QUESTIONS,
                    buildQuestionsWithLlm(cleanInput, requirement, List.of("destination")));
        }
        return Map.of(STATUS, "READY", QUESTIONS, List.of());
    }

    private Map<String, Object> recommendDestinationsWithLlm(OverAllState state) {
        TravelRequirementDTO requirement =
                state.value(REQUIREMENT, TravelRequirementDTO.class).orElseThrow();
        String cleanInput = state.value(CLEAN_INPUT, "");
        log.info("节点[目的地推荐]：真实调用 ChatModel，根据偏好推荐 3 个候选目的地。偏好={}", requirement.getPreferences());

        String json =
                callLlmForJson(
                        """
                        你是旅行目的地推荐器。请只输出 JSON，不要 Markdown，不要解释。
                        根据用户输入和已抽取需求，推荐 3 个适合的中国旅行目的地。

                        JSON 字段必须是：
                        {
                          "suggestions": [
                            {
                              "name": "目的地名称",
                              "reason": "推荐理由",
                              "tags": ["标签"],
                              "recommendedDays": 3
                            }
                          ]
                        }

                        用户输入：
                        %s

                        已抽取需求：
                        %s
                        """
                                .formatted(cleanInput, toJson(requirement)));
        Map<String, Object> payload = readMap(json);
        return Map.of(DESTINATION_SUGGESTIONS, destinationSuggestions(payload.get("suggestions")));
    }

    private Map<String, Object> checkConflictsWithLlm(OverAllState state) {
        TravelRequirementDTO requirement =
                state.value(REQUIREMENT, TravelRequirementDTO.class).orElseThrow();
        String cleanInput = state.value(CLEAN_INPUT, "");
        log.info("节点[冲突检查]：真实调用 ChatModel，判断天数、预算、节奏等是否存在明显冲突。");

        String json =
                callLlmForJson(
                        """
                        你是旅行需求冲突检查器。请只输出 JSON，不要 Markdown，不要解释。
                        判断需求是否存在明显冲突。

                        常见冲突包括：
                        - days < 1 或 days > 7
                        - 多人多天但预算明显过低
                        - 用户要求轻松，但条件明显要求高强度
                        - 出行日期已经过去

                        JSON 字段必须是：
                        {
                          "status": "READY",
                          "conflicts": [
                            {
                              "type": "冲突类型",
                              "message": "冲突说明",
                              "suggestion": "处理建议"
                            }
                          ]
                        }

                        status 只能是 READY 或 CONFLICT。

                        用户输入：
                        %s

                        标准化需求：
                        %s
                        """
                                .formatted(cleanInput, toJson(requirement)));
        Map<String, Object> payload = readMap(json);
        String status = "CONFLICT".equals(payload.get("status")) ? "CONFLICT" : "READY";
        return Map.of(STATUS, status, CONFLICTS, conflicts(payload.get("conflicts")));
    }

    private Map<String, Object> assembleResult(OverAllState state) {
        AnalyzeInput input = state.value(INPUT_REQUEST, AnalyzeInput.class).orElseThrow();
        String status = state.value(STATUS, "NEED_MORE_INFO");
        TravelRequirementDTO requirement =
                state.value(REQUIREMENT, TravelRequirementDTO.class).orElse(null);
        List<QuestionDTO> questions = state.value(QUESTIONS, List.<QuestionDTO>of());
        List<DestinationSuggestionDTO> suggestions =
                state.value(DESTINATION_SUGGESTIONS, List.<DestinationSuggestionDTO>of());
        List<ConflictDTO> conflicts = state.value(CONFLICTS, List.<ConflictDTO>of());
        log.info("节点[组装分析结果]：组装 TripAnalyzeResponse，最终状态={}", status);

        return Map.of(
                RESULT,
                new TripAnalyzeResponse(
                        input.getConversationId(),
                        status,
                        requirement,
                        questions,
                        suggestions,
                        conflicts,
                        0));
    }

    private List<QuestionDTO> buildQuestionsWithLlm(
            String cleanInput, TravelRequirementDTO requirement, List<String> missingFields) {
        log.info("节点[完整性检查]：真实调用 ChatModel，为缺失字段生成追问。缺失字段={}", missingFields);
        String json =
                callLlmForJson(
                        """
                        你是旅行需求追问生成器。请只输出 JSON，不要 Markdown，不要解释。
                        根据缺失字段生成简短、自然的中文追问。

                        JSON 字段必须是：
                        {
                          "questions": [
                            {
                              "field": "字段名",
                              "question": "追问内容",
                              "required": true
                            }
                          ]
                        }

                        缺失字段：
                        %s

                        用户输入：
                        %s

                        当前需求：
                        %s
                        """
                                .formatted(missingFields, cleanInput, toJson(requirement)));
        return questions(readMap(json).get("questions"));
    }

    private String callLlmForJson(String prompt) {
        String content = chatModel.call(new Prompt(prompt)).getResult().getOutput().getText();
        String json = extractJsonObject(content);
        log.info("LLM JSON 返回：{}", json);
        return json;
    }

    private ChatModel buildDeepSeekChatModel() {
        DeepSeekApi api =
                DeepSeekApi.builder()
                        .baseUrl(
                                withoutTrailingSlash(
                                        optionalEnv(
                                                "DEEPSEEK_BASE_URL", "https://api.deepseek.com")))
                        .apiKey(Objects.requireNonNull(System.getenv("DEEPSEEK_API_KEY")))
                        .build();
        DeepSeekChatOptions options =
                DeepSeekChatOptions.builder()
                        .model(optionalEnv("DEEPSEEK_MODEL", "deepseek-chat"))
                        .temperature(0.0)
                        .maxTokens(1200)
                        .build();
        return DeepSeekChatModel.builder().deepSeekApi(api).defaultOptions(options).build();
    }

    private TripAnalyzeResponse result(List<NodeOutput> outputs) {
        return outputs.get(outputs.size() - 1)
                .state()
                .value(RESULT, TripAnalyzeResponse.class)
                .orElseThrow();
    }

    private List<String> nodeNames(List<NodeOutput> outputs) {
        return outputs.stream().map(NodeOutput::node).toList();
    }

    private List<String> destinationNames(List<?> suggestions) {
        return suggestions.stream().map(item -> valueOf(item, "name")).toList();
    }

    @SuppressWarnings("unchecked")
    private String valueOf(Object item, String field) {
        if (item instanceof DestinationSuggestionDTO suggestion && "name".equals(field)) {
            return suggestion.getName();
        }
        if (item instanceof Map<?, ?> map) {
            return String.valueOf(((Map<String, Object>) map).get(field));
        }
        throw new IllegalArgumentException("Unsupported item type: " + item.getClass());
    }

    @SuppressWarnings("unchecked")
    private List<QuestionDTO> questions(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .map(
                        item ->
                                new QuestionDTO(
                                        defaultString(item.get("field"), "unknown"),
                                        defaultString(item.get("question"), "请补充旅行信息。"),
                                        booleanValue(item.get("required"), true)))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<DestinationSuggestionDTO> destinationSuggestions(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .limit(3)
                .map(item -> (Map<String, Object>) item)
                .map(
                        item ->
                                new DestinationSuggestionDTO(
                                        defaultString(item.get("name"), "未知目的地"),
                                        defaultString(item.get("reason"), ""),
                                        stringList(item.get("tags")),
                                        defaultInt(item.get("recommendedDays"), 3)))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<ConflictDTO> conflicts(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .map(
                        item ->
                                new ConflictDTO(
                                        defaultString(item.get("type"), "UNKNOWN"),
                                        defaultString(item.get("message"), ""),
                                        defaultString(item.get("suggestion"), "")))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new AssertionError("LLM 返回不是合法 JSON：" + json, exception);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new AssertionError("测试对象无法序列化为 JSON", exception);
        }
    }

    private String extractJsonObject(String content) {
        String text = content.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```json\\s*", "").replaceFirst("^```\\s*", "");
            text = text.replaceFirst("\\s*```$", "");
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new AssertionError("LLM 返回中没有 JSON 对象：" + content);
        }
        return text.substring(start, end + 1);
    }

    private void append(StringBuilder builder, String value) {
        if (value != null && !value.endsWith("null")) {
            if (!builder.isEmpty()) {
                builder.append("；");
            }
            builder.append(value);
        }
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() || "null".equalsIgnoreCase(text) ? null : text;
    }

    private String defaultString(Object value, String defaultValue) {
        String text = stringValue(value);
        return text == null ? defaultValue : text;
    }

    private Integer intValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = String.valueOf(value).replaceAll("[^0-9-]", "");
        return text.isBlank() ? null : Integer.valueOf(text);
    }

    private Integer defaultInt(Object value, Integer defaultValue) {
        Integer number = intValue(value);
        return number == null ? defaultValue : number;
    }

    private boolean booleanValue(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return ((List<Object>) list)
                .stream().map(this::stringValue).filter(Objects::nonNull).toList();
    }

    private String normalizePace(Object value) {
        String pace = defaultString(value, "NORMAL");
        return switch (pace) {
            case "LIGHT", "NORMAL", "INTENSIVE" -> pace;
            default -> "NORMAL";
        };
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String optionalEnv(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String withoutTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private class AnalyzeInput {

        private String conversationId;
        private String userInput;
        private FormInput formInput;
        private List<String> extraAnswers;
        private String selectedDestination;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private class FormInput {

        private String departure;
        private String destination;
        private Integer days;
        private Integer budget;
        private Integer peopleCount;
        private List<String> preferences;
    }
}
