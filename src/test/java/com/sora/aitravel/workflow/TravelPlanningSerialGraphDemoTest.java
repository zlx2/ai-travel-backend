package com.sora.aitravel.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategyFactoryBuilder;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
class TravelPlanningSerialGraphDemoTest {

    private static final String USER_INPUT = "userInput";
    private static final String NORMALIZED_INPUT = "normalizedInput";
    private static final String REQUIREMENT = "requirement";
    private static final String ANALYZE_STATUS = "analyzeStatus";
    private static final String FOLLOW_UP_QUESTION = "followUpQuestion";
    private static final String DESTINATION_OPTIONS = "destinationOptions";
    private static final String CONFLICTS = "conflicts";
    private static final String AMAP_CONTEXT = "amapContext";
    private static final String RENTAL_QUOTE = "rentalQuote";
    private static final String PROMPT = "prompt";
    private static final String RAW_PLAN = "rawPlan";
    private static final String RESULT = "result";

    @Test
    void shouldStopAndAskFollowUpWhenRequirementIsInsufficient() throws Exception {
        CompiledGraph graph = buildSerialTravelPlanningGraph();

        List<NodeOutput> outputs =
                graph.stream(Map.of(USER_INPUT, "想出去玩，帮我规划一下")).collectList().block();

        assertThat(outputs).isNotNull();
        assertThat(nodeNames(outputs))
                .containsSubsequence(
                        StateGraph.START,
                        "input-normalize",
                        "llm-info-extract",
                        "completeness-check",
                        "follow-up-question",
                        "result-merge",
                        StateGraph.END);
        assertThat(nodeNames(outputs)).doesNotContain("amap-poi-query", "llm-trip-generate");

        OverAllState finalState = outputs.get(outputs.size() - 1).state();
        assertThat(finalState.value(ANALYZE_STATUS, String.class)).contains("NEED_MORE_INFO");
        assertThat(finalState.value(FOLLOW_UP_QUESTION, String.class).orElseThrow())
                .contains("请补充出发地、目的地和天数");
    }

    @Test
    void shouldRunSerialPlanningFlowWithAmapAndLlmInteractionPlaceholders() throws Exception {
        CompiledGraph graph = buildSerialTravelPlanningGraph();

        List<NodeOutput> outputs =
                graph.stream(Map.of(USER_INPUT, "我从上海出发去杭州玩3天，喜欢美食和夜景，想租车")).collectList().block();

        assertThat(outputs).isNotNull();
        assertThat(nodeNames(outputs))
                .containsSubsequence(
                        StateGraph.START,
                        "input-normalize",
                        "llm-info-extract",
                        "completeness-check",
                        "conflict-check",
                        "amap-poi-query",
                        "rental-quote-preview",
                        "prompt-build",
                        "llm-trip-generate",
                        "result-merge",
                        StateGraph.END);

        OverAllState finalState = outputs.get(outputs.size() - 1).state();
        assertThat(finalState.value(ANALYZE_STATUS, String.class)).contains("READY");
        assertThat(finalState.value(AMAP_CONTEXT)).isPresent();
        assertThat(finalState.value(RENTAL_QUOTE)).isPresent();
        assertThat(finalState.value(RAW_PLAN, String.class).orElseThrow()).contains("杭州3日旅行方案");
        assertThat(finalState.value(RESULT, PlanningResult.class))
                .get()
                .extracting(PlanningResult::status)
                .isEqualTo("GENERATED");
    }

    @Test
    void shouldRecommendDestinationBeforeGenerationWhenDestinationIsMissing() throws Exception {
        CompiledGraph graph = buildSerialTravelPlanningGraph();

        List<NodeOutput> outputs =
                graph.stream(Map.of(USER_INPUT, "我从上海出发玩3天，喜欢美食和夜景")).collectList().block();

        assertThat(outputs).isNotNull();
        assertThat(nodeNames(outputs))
                .containsSubsequence(
                        "completeness-check",
                        "llm-destination-recommend",
                        "conflict-check",
                        "amap-poi-query",
                        "prompt-build",
                        "llm-trip-generate");

        OverAllState finalState = outputs.get(outputs.size() - 1).state();
        assertThat(finalState.value(ANALYZE_STATUS, String.class))
                .contains("NEED_DESTINATION_RECOMMEND");
        assertThat(finalState.value(DESTINATION_OPTIONS)).contains(List.of("杭州", "苏州", "南京"));
        assertThat(finalState.value(RESULT, PlanningResult.class))
                .get()
                .extracting(PlanningResult::destination)
                .isEqualTo("杭州");
    }

    private CompiledGraph buildSerialTravelPlanningGraph() throws Exception {
        StateGraph graph =
                new StateGraph(
                        "travel-planning-serial-demo",
                        new KeyStrategyFactoryBuilder()
                                .addStrategy(USER_INPUT, new ReplaceStrategy())
                                .addStrategy(NORMALIZED_INPUT, new ReplaceStrategy())
                                .addStrategy(REQUIREMENT, new ReplaceStrategy())
                                .addStrategy(ANALYZE_STATUS, new ReplaceStrategy())
                                .addStrategy(FOLLOW_UP_QUESTION, new ReplaceStrategy())
                                .addStrategy(DESTINATION_OPTIONS, new ReplaceStrategy())
                                .addStrategy(CONFLICTS, new ReplaceStrategy())
                                .addStrategy(AMAP_CONTEXT, new ReplaceStrategy())
                                .addStrategy(RENTAL_QUOTE, new ReplaceStrategy())
                                .addStrategy(PROMPT, new ReplaceStrategy())
                                .addStrategy(RAW_PLAN, new ReplaceStrategy())
                                .addStrategy(RESULT, new ReplaceStrategy())
                                .build());

        graph.addNode("input-normalize", AsyncNodeAction.node_async(this::normalizeInput));
        graph.addNode(
                "llm-info-extract", AsyncNodeAction.node_async(this::extractRequirementByLlm));
        graph.addNode("completeness-check", AsyncNodeAction.node_async(this::checkCompleteness));
        graph.addNode("follow-up-question", AsyncNodeAction.node_async(this::askFollowUp));
        graph.addNode(
                "llm-destination-recommend",
                AsyncNodeAction.node_async(this::recommendDestinationByLlm));
        graph.addNode("conflict-check", AsyncNodeAction.node_async(this::checkConflict));
        graph.addNode("amap-poi-query", AsyncNodeAction.node_async(this::queryAmapPoi));
        graph.addNode("rental-quote-preview", AsyncNodeAction.node_async(this::previewRentalQuote));
        graph.addNode("prompt-build", AsyncNodeAction.node_async(this::buildPrompt));
        graph.addNode("llm-trip-generate", AsyncNodeAction.node_async(this::generateTripPlanByLlm));
        graph.addNode("result-merge", AsyncNodeAction.node_async(this::mergeResult));

        graph.addEdge(StateGraph.START, "input-normalize");
        graph.addEdge("input-normalize", "llm-info-extract");
        graph.addEdge("llm-info-extract", "completeness-check");
        graph.addConditionalEdges(
                "completeness-check",
                AsyncEdgeAction.edge_async(state -> state.value(ANALYZE_STATUS, "NEED_MORE_INFO")),
                Map.of(
                        "NEED_MORE_INFO",
                        "follow-up-question",
                        "NEED_DESTINATION_RECOMMEND",
                        "llm-destination-recommend",
                        "READY",
                        "conflict-check"));
        graph.addEdge("follow-up-question", "result-merge");
        graph.addEdge("llm-destination-recommend", "conflict-check");
        graph.addEdge("conflict-check", "amap-poi-query");
        graph.addConditionalEdges(
                "amap-poi-query",
                AsyncEdgeAction.edge_async(
                        state -> {
                            TravelRequirement requirement =
                                    state.value(REQUIREMENT, TravelRequirement.class).orElseThrow();
                            return requirement.needRental() ? "NEED_RENTAL_QUOTE" : "NO_RENTAL";
                        }),
                Map.of("NEED_RENTAL_QUOTE", "rental-quote-preview", "NO_RENTAL", "prompt-build"));
        graph.addEdge("rental-quote-preview", "prompt-build");
        graph.addEdge("prompt-build", "llm-trip-generate");
        graph.addEdge("llm-trip-generate", "result-merge");
        graph.addEdge("result-merge", StateGraph.END);

        return graph.compile();
    }

    private Map<String, Object> normalizeInput(OverAllState state) {
        String userInput = state.value(USER_INPUT, "");
        String normalized = userInput.trim().replaceAll("\\s+", " ");
        log.info("Node[input-normalize]: normalize user input. input={}", normalized);
        return Map.of(NORMALIZED_INPUT, normalized);
    }

    private Map<String, Object> extractRequirementByLlm(OverAllState state) {
        String input = state.value(NORMALIZED_INPUT, "");
        log.info("Node[llm-info-extract]: call ChatModel to extract structured requirement.");

        TravelRequirement requirement =
                new TravelRequirement(
                        input.contains("上海") ? "上海" : null,
                        input.contains("杭州") ? "杭州" : null,
                        input.contains("3天") ? 3 : null,
                        containsAny(input, "美食", "夜景") ? List.of("美食", "夜景") : List.of(),
                        containsAny(input, "租车", "自驾"));
        return Map.of(REQUIREMENT, requirement);
    }

    private Map<String, Object> checkCompleteness(OverAllState state) {
        TravelRequirement requirement =
                state.value(REQUIREMENT, TravelRequirement.class).orElseThrow();
        String status;
        if (isBlank(requirement.departure()) || requirement.days() == null) {
            status = "NEED_MORE_INFO";
        } else if (isBlank(requirement.destination())) {
            status = "NEED_DESTINATION_RECOMMEND";
        } else {
            status = "READY";
        }
        log.info("Node[completeness-check]: analyze status={}", status);
        return Map.of(ANALYZE_STATUS, status);
    }

    private Map<String, Object> askFollowUp(OverAllState state) {
        log.info("Node[follow-up-question]: build follow-up question and stop planning.");
        return Map.of(FOLLOW_UP_QUESTION, "请补充出发地、目的地和天数，我再继续规划。");
    }

    private Map<String, Object> recommendDestinationByLlm(OverAllState state) {
        TravelRequirement requirement =
                state.value(REQUIREMENT, TravelRequirement.class).orElseThrow();
        log.info("Node[llm-destination-recommend]: call LLM to recommend candidate destinations.");

        List<String> options = List.of("杭州", "苏州", "南京");
        TravelRequirement completed =
                new TravelRequirement(
                        requirement.departure(),
                        options.get(0),
                        requirement.days(),
                        requirement.preferences(),
                        requirement.needRental());
        return Map.of(DESTINATION_OPTIONS, options, REQUIREMENT, completed);
    }

    private Map<String, Object> checkConflict(OverAllState state) {
        TravelRequirement requirement =
                state.value(REQUIREMENT, TravelRequirement.class).orElseThrow();
        log.info(
                "Node[conflict-check]: check route/date/budget conflicts. departure={}, destination={}, days={}",
                requirement.departure(),
                requirement.destination(),
                requirement.days());
        return Map.of(CONFLICTS, List.of());
    }

    private Map<String, Object> queryAmapPoi(OverAllState state) {
        TravelRequirement requirement =
                state.value(REQUIREMENT, TravelRequirement.class).orElseThrow();
        log.info(
                "Node[amap-poi-query]: call AmapPoiClient for scenic/food/hotel/rental POI. city={}",
                requirement.destination());

        AmapContext amapContext =
                new AmapContext(
                        List.of(
                                requirement.destination() + "西湖景区",
                                requirement.destination() + "城市夜景区"),
                        List.of(
                                requirement.destination() + "本地菜餐厅",
                                requirement.destination() + "夜市"),
                        List.of(requirement.destination() + "核心商圈酒店区"),
                        requirement.needRental()
                                ? List.of(requirement.destination() + "高铁站租车点")
                                : List.of());
        return Map.of(AMAP_CONTEXT, amapContext);
    }

    private Map<String, Object> previewRentalQuote(OverAllState state) {
        TravelRequirement requirement =
                state.value(REQUIREMENT, TravelRequirement.class).orElseThrow();
        AmapContext amapContext = state.value(AMAP_CONTEXT, AmapContext.class).orElseThrow();
        log.info(
                "Node[rental-quote-preview]: calculate rental quote from requirement and Amap rental stores.");

        RentalQuote quote =
                new RentalQuote(
                        requirement.destination() + "经济型轿车",
                        amapContext.rentalStores().get(0),
                        requirement.days() * 220);
        return Map.of(RENTAL_QUOTE, quote);
    }

    private Map<String, Object> buildPrompt(OverAllState state) {
        TravelRequirement requirement =
                state.value(REQUIREMENT, TravelRequirement.class).orElseThrow();
        AmapContext amapContext = state.value(AMAP_CONTEXT, AmapContext.class).orElseThrow();
        Object quote = state.value(RENTAL_QUOTE).orElse(null);
        log.info(
                "Node[prompt-build]: build final prompt with requirement, Amap context and quote.");

        String prompt =
                """
                请生成旅行计划：
                出发地：%s
                目的地：%s
                天数：%s
                偏好：%s
                景点：%s
                美食：%s
                酒店区域：%s
                租车报价：%s
                """
                        .formatted(
                                requirement.departure(),
                                requirement.destination(),
                                requirement.days(),
                                requirement.preferences(),
                                amapContext.scenicPois(),
                                amapContext.foodPois(),
                                amapContext.hotelAreas(),
                                quote);
        return Map.of(PROMPT, prompt);
    }

    private Map<String, Object> generateTripPlanByLlm(OverAllState state) {
        TravelRequirement requirement =
                state.value(REQUIREMENT, TravelRequirement.class).orElseThrow();
        String prompt = state.value(PROMPT, "");
        log.info("Node[llm-trip-generate]: call ChatModel once, non-streaming. prompt={}", prompt);

        String rawPlan =
                """
                %s%s日旅行方案
                Day1: 抵达后游览核心景区并安排本地菜。
                Day2: 白天城市深度游，晚上看夜景。
                Day3: 轻松收尾，预留返程时间。
                """
                        .formatted(requirement.destination(), requirement.days());
        return Map.of(RAW_PLAN, rawPlan);
    }

    private Map<String, Object> mergeResult(OverAllState state) {
        String status = state.value(ANALYZE_STATUS, "NEED_MORE_INFO");
        TravelRequirement requirement =
                state.value(REQUIREMENT, TravelRequirement.class).orElse(null);
        String followUpQuestion = state.value(FOLLOW_UP_QUESTION, String.class).orElse(null);
        String rawPlan = state.value(RAW_PLAN, String.class).orElse(null);
        log.info("Node[result-merge]: merge graph state to controller response DTO.");

        PlanningResult result =
                "NEED_MORE_INFO".equals(status)
                        ? new PlanningResult("NEED_MORE_INFO", null, followUpQuestion, null)
                        : new PlanningResult(
                                "GENERATED",
                                requirement == null ? null : requirement.destination(),
                                null,
                                rawPlan);
        return Map.of(RESULT, result);
    }

    private List<String> nodeNames(List<NodeOutput> outputs) {
        return outputs.stream().map(NodeOutput::node).toList();
    }

    private boolean containsAny(String input, String... keywords) {
        for (String keyword : keywords) {
            if (input.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record TravelRequirement(
            String departure,
            String destination,
            Integer days,
            List<String> preferences,
            boolean needRental) {}

    private record AmapContext(
            List<String> scenicPois,
            List<String> foodPois,
            List<String> hotelAreas,
            List<String> rentalStores) {}

    private record RentalQuote(String vehicleGroup, String pickupStore, int totalPriceYuan) {}

    private record PlanningResult(
            String status, String destination, String followUpQuestion, String planText) {}
}
