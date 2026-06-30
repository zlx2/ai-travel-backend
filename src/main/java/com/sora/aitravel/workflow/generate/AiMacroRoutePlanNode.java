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

/** Lets AI propose multiple multi-day route skeletons from factual area anchors. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiMacroRoutePlanNode {
    private static final String PROMPT =
            """
            你是自驾旅行规划师。请基于候选区域为用户生成 2-3 个多日路线骨架方案。

            用户需求：
            目的地：%s
            天数：%d
            人数：%d
            节奏：%s
            偏好：%s
            租车：%s

            候选区域锚点：
            %s

            规则：
            1. 只能使用候选区域锚点 id 字段的完整值，不允许使用高德 POI id、名称或编造区域；
            2. 每一天必须包含 startAreaId、focusAreaIds、endAreaId、stayAreaId；
            3. Day N 的 stayAreaId 应成为 Day N+1 的 startAreaId，除非有明确理由；
            4. 多日路线尽量沿一个方向推进，避免离开远郊后又回到同一远郊；
            5. 住宿区域要服务第二天出发；
            6. 如果租车为“是”，Day 1 的 startAreaId 必须使用 role=PICKUP 的候选；
            7. Day N 的 stayAreaId 必须等于 Day N+1 的 startAreaId；
            8. 不要输出具体时间和景点顺序。

            只返回 JSON 对象：
            {
              "plans": [
                {
                  "id": "plan_a",
                  "routeShape": "LOOP",
                  "reason": "为什么这样走",
                  "warnings": [],
                  "days": [
                    {
                      "day": 1,
                      "startAreaId": "候选区域锚点 id 的完整值",
                      "focusAreaIds": ["候选区域锚点 id 的完整值"],
                      "endAreaId": "候选区域锚点 id 的完整值",
                      "stayAreaId": "候选区域锚点 id 的完整值",
                      "theme": "当天主题",
                      "reason": "当天安排理由"
                    }
                  ]
                }
              ]
            }
            """;

    private final AiGateway aiGateway;
    private final ObjectMapper objectMapper;

    public void execute(GenerateWorkflowContext context) {
        CandidatePool pool = context.getCandidatePool();
        if (pool == null || pool.getAreaAnchors() == null || pool.getAreaAnchors().isEmpty()) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "缺少可用于路线骨架的区域候选");
        }
        TravelRequirementDTO requirement = context.getRequirement();
        List<AreaAnchorCandidate> macroAnchors = selectedMacroAnchors(pool);
        String prompt =
                PROMPT.formatted(
                        requirement.getDestination(),
                        value(requirement.getDays(), 1),
                        value(requirement.getPeopleCount(), 1),
                        requirement.getPace(),
                        String.join("、", requirement.getPreferences() == null ? List.of() : requirement.getPreferences()),
                        context.getSelectedQuote() == null ? "否" : "是",
                        anchorText(macroAnchors));
        log.info(
                "节点[ai-macro-route-plan]：宏观路线候选区域已精简，anchors={}, pickup={}, scenic={}, stay={}",
                macroAnchors.size(),
                countRole(macroAnchors, "PICKUP"),
                countRole(macroAnchors, "SCENIC_CLUSTER"),
                countRole(macroAnchors, "STAY_AREA"));
        String json = aiGateway.callJsonObject(AiScene.TRIP_GENERATE, prompt);
        List<MacroRoutePlan> plans = parsePlans(json, value(requirement.getDays(), 1));
        normalizeHardRouteContracts(context, plans);
        context.setMacroRoutePlans(plans);
        log.info("节点[ai-macro-route-plan]：AI 已生成路线骨架候选，plans={}", plans.size());
    }

    private void normalizeHardRouteContracts(GenerateWorkflowContext context, List<MacroRoutePlan> plans) {
        if (plans == null || plans.isEmpty()) {
            return;
        }
        CandidatePool pool = context.getCandidatePool();
        boolean rentalEnabled = context.getSelectedQuote() != null;
        String pickupId =
                pool == null || pool.getPickupAnchor() == null ? null : pool.getPickupAnchor().getId();
        for (MacroRoutePlan plan : plans) {
            if (plan.getDays() == null || plan.getDays().isEmpty()) {
                continue;
            }
            plan.getDays().sort(java.util.Comparator.comparing(day -> value(day.getDay(), 0)));
            if (rentalEnabled && pickupId != null) {
                plan.getDays().get(0).setStartAreaId(pickupId);
            }
            for (int index = 1; index < plan.getDays().size(); index++) {
                MacroRouteDay previous = plan.getDays().get(index - 1);
                MacroRouteDay current = plan.getDays().get(index);
                if (previous.getStayAreaId() != null && !previous.getStayAreaId().isBlank()) {
                    current.setStartAreaId(previous.getStayAreaId());
                }
            }
        }
    }

    private List<MacroRoutePlan> parsePlans(String json, int days) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode plansNode = root.path("plans");
            if (!plansNode.isArray() || plansNode.isEmpty()) {
                throw new IllegalStateException("AI 未返回 plans");
            }
            List<MacroRoutePlan> plans = new ArrayList<>();
            for (JsonNode item : plansNode) {
                MacroRoutePlan plan = new MacroRoutePlan();
                plan.setId(text(item, "id", "plan_" + (plans.size() + 1)));
                plan.setRouteShape(text(item, "routeShape", "LOOP"));
                plan.setReason(text(item, "reason", ""));
                plan.setWarnings(strings(item.path("warnings")));
                plan.setDays(parseDays(item.path("days")));
                if (plan.getDays().size() == days) {
                    plans.add(plan);
                }
            }
            if (plans.isEmpty()) {
                throw new IllegalStateException("AI 返回的路线骨架天数不匹配");
            }
            return plans;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_FORMAT_ERROR, "路线骨架 JSON 解析失败");
        }
    }

    private List<MacroRouteDay> parseDays(JsonNode daysNode) {
        List<MacroRouteDay> days = new ArrayList<>();
        if (!daysNode.isArray()) {
            return days;
        }
        for (JsonNode item : daysNode) {
            days.add(new MacroRouteDay(
                    item.path("day").asInt(),
                    text(item, "startAreaId", null),
                    strings(item.path("focusAreaIds")),
                    text(item, "endAreaId", null),
                    text(item, "stayAreaId", null),
                    text(item, "theme", null),
                    text(item, "reason", null)));
        }
        return days;
    }

    private String anchorText(List<AreaAnchorCandidate> anchors) {
        List<String> lines = new ArrayList<>();
        for (AreaAnchorCandidate anchor : anchors) {
            lines.add("- id=" + anchor.getId()
                    + "；role=" + anchor.getRole()
                    + "；name=" + anchor.getName()
                    + "；city=" + anchor.getCity()
                    + "；area=" + anchor.getArea());
        }
        return String.join("\n", lines);
    }

    private List<AreaAnchorCandidate> selectedMacroAnchors(CandidatePool pool) {
        List<AreaAnchorCandidate> all = pool.getAreaAnchors() == null ? List.of() : pool.getAreaAnchors();
        List<AreaAnchorCandidate> result = new ArrayList<>();
        addIfPresent(result, pool.getPickupAnchor());
        addRole(result, all, "SCENIC_CLUSTER", 24);
        addRole(result, all, "STAY_AREA", 12);
        return result;
    }

    private void addRole(
            List<AreaAnchorCandidate> result, List<AreaAnchorCandidate> all, String role, int limit) {
        all.stream()
                .filter(anchor -> role.equals(anchor.getRole()))
                .filter(anchor -> result.stream().noneMatch(existing -> existing.getId().equals(anchor.getId())))
                .limit(limit)
                .forEach(result::add);
    }

    private void addIfPresent(List<AreaAnchorCandidate> result, AreaAnchorCandidate anchor) {
        if (anchor != null) {
            result.add(anchor);
        }
    }

    private long countRole(List<AreaAnchorCandidate> anchors, String role) {
        return anchors.stream().filter(anchor -> role.equals(anchor.getRole())).count();
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
