package com.sora.aitravel.workflow.generate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sora.aitravel.ai.AiGateway;
import com.sora.aitravel.ai.AiScene;
import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** AI reviewer that selects or revises the best macro route using AMap facts. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiRouteCriticNode {
    private static final String PROMPT =
            """
            你是自驾路线审稿人。请基于候选路线方案和高德事实，选择或微调最合理的多日路线骨架。

            用户需求：
            目的地：%s
            天数：%d
            节奏：%s
            偏好：%s

            候选方案与事实：
            %s

            评审目标：
            1. 选择整体最自然、少折返、适合自驾的方案；
            2. 住宿区域要服务第二天出发；
            3. 如果候选方案有轻微问题，可以返回 revisedPlan 微调；
            4. revisedPlan 仍只能使用原方案中的区域 id，不要编造新 id；
            5. Day N 的 stayAreaId 必须等于 Day N+1 的 startAreaId；
            6. 不要输出具体景点和时间。

            只返回 JSON 对象：
            {
              "selectedPlanId": "plan_a",
              "score": 88,
              "warnings": ["提醒"],
              "reason": "选择理由",
              "revisedPlan": null
            }
            """;

    private final AiGateway aiGateway;
    private final ObjectMapper objectMapper;

    public void execute(GenerateWorkflowContext context) {
        if (context.getMacroRoutePlans() == null || context.getMacroRoutePlans().isEmpty()) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "缺少路线方案，无法审稿");
        }
        TravelRequirementDTO requirement = context.getRequirement();
        String prompt = PROMPT.formatted(
                requirement.getDestination(),
                value(requirement.getDays(), 1),
                requirement.getPace(),
                String.join("、", requirement.getPreferences() == null ? List.of() : requirement.getPreferences()),
                plansWithFacts(context));
        String json = aiGateway.callJsonObject(AiScene.TRIP_GENERATE, prompt);
        RouteCriticResult result = parse(json, context.getMacroRoutePlans());
        context.setRouteCriticResult(result);
        log.info("节点[ai-route-critic]：路线审稿完成，selected={}, score={}", result.getSelectedPlanId(), result.getScore());
    }

    private RouteCriticResult parse(String json, List<MacroRoutePlan> plans) {
        try {
            JsonNode root = objectMapper.readTree(json);
            String selectedId = text(root, "selectedPlanId", null);
            MacroRoutePlan selected = plans.stream()
                    .filter(plan -> plan.getId().equals(selectedId))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.AI_RESPONSE_FORMAT_ERROR, "路线审稿选择了不存在的方案"));
            MacroRoutePlan revised = null;
            if (root.hasNonNull("revisedPlan") && root.path("revisedPlan").isObject()) {
                revised = objectMapper.treeToValue(root.path("revisedPlan"), MacroRoutePlan.class);
            }
            return new RouteCriticResult(
                    selected.getId(),
                    revised,
                    root.path("score").isInt() ? root.path("score").asInt() : 80,
                    strings(root.path("warnings")),
                    text(root, "reason", selected.getReason()));
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_FORMAT_ERROR, "路线审稿 JSON 解析失败");
        }
    }

    private String plansWithFacts(GenerateWorkflowContext context) {
        List<String> blocks = new ArrayList<>();
        for (MacroRoutePlan plan : context.getMacroRoutePlans()) {
            MacroRouteFact fact = context.getMacroRouteFacts() == null ? null : context.getMacroRouteFacts().stream()
                    .filter(item -> plan.getId().equals(item.getPlanId()))
                    .findFirst()
                    .orElse(null);
            blocks.add("方案 " + plan.getId() + "，shape=" + plan.getRouteShape()
                    + "，reason=" + plan.getReason()
                    + "\n天安排=" + plan.getDays()
                    + "\n高德事实=" + fact);
        }
        return String.join("\n\n", blocks);
    }

    private List<String> strings(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(item -> {
                if (!item.asText("").isBlank()) {
                    values.add(item.asText());
                }
            });
        }
        return values;
    }

    private String text(JsonNode node, String field, String fallback) {
        String value = node.path(field).asText("");
        return value.isBlank() ? fallback : value;
    }

    private int value(Integer value, int fallback) {
        return value == null ? fallback : value;
    }
}
