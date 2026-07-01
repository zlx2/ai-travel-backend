package com.sora.aitravel.workflow.generate;

import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.CANDIDATE_POOL;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.MACRO_ROUTE_PLANS;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.REQUIREMENT;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.SELECTED_QUOTE;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sora.aitravel.ai.AiGateway;
import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.dto.model.RentalQuoteOptionDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.workflow.generate.state.TripGraphStateCodec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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

    public Map<String, Object> execute(OverAllState state) {
        List<MacroRoutePlan> plans =
                generatePlans(
                        TripGraphStateCodec.required(state, CANDIDATE_POOL, CandidatePool.class),
                        TripGraphStateCodec.required(
                                state, REQUIREMENT, TravelRequirementDTO.class),
                        TripGraphStateCodec.optional(
                                        state, SELECTED_QUOTE, RentalQuoteOptionDTO.class)
                                .orElse(null));
        return TripGraphStateCodec.patch(MACRO_ROUTE_PLANS, plans);
    }

    private List<MacroRoutePlan> generatePlans(
            CandidatePool pool,
            TravelRequirementDTO requirement,
            RentalQuoteOptionDTO selectedQuote) {
        if (pool == null || pool.getAreaAnchors() == null || pool.getAreaAnchors().isEmpty()) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "缺少可用于路线骨架的区域候选");
        }
        List<AreaAnchorCandidate> macroAnchors = selectedMacroAnchors(pool);
        log.info(
                "节点[ai-macro-route-plan]：使用规则生成宏观路线骨架，anchors={}, pickup={}, scenic={}, stay={}",
                macroAnchors.size(),
                countRole(macroAnchors, "PICKUP"),
                countRole(macroAnchors, "SCENIC_CLUSTER"),
                countRole(macroAnchors, "STAY_AREA"));
        List<MacroRoutePlan> plans =
                generateRulePlans(pool, macroAnchors, requirement, selectedQuote);
        normalizeHardRouteContracts(pool, selectedQuote, plans);
        log.info("节点[ai-macro-route-plan]：已生成路线骨架候选，plans={}", plans.size());
        return plans;
    }

    private List<MacroRoutePlan> generateRulePlans(
            CandidatePool pool,
            List<AreaAnchorCandidate> anchors,
            TravelRequirementDTO requirement,
            RentalQuoteOptionDTO selectedQuote) {
        int days = value(requirement.getDays(), 1);
        List<AreaAnchorCandidate> scenicAnchors =
                anchors.stream()
                        .filter(anchor -> "SCENIC_CLUSTER".equals(anchor.getRole()))
                        .toList();
        List<AreaAnchorCandidate> stayAnchors =
                anchors.stream().filter(anchor -> "STAY_AREA".equals(anchor.getRole())).toList();
        if (scenicAnchors.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "缺少可用于路线骨架的景点区域候选");
        }
        List<MacroRoutePlan> plans = new ArrayList<>();
        String routeShape = routeShape(requirement, selectedQuote);
        List<AreaAnchorCandidate> ordered =
                orderScenicAnchors(routeShape, days, scenicAnchors, pool);
        plans.add(
                rulePlan("plan_a", routeShape, days, ordered, stayAnchors, pool, selectedQuote, 0));
        if (scenicAnchors.size() > 1) {
            plans.add(
                    rulePlan(
                            "plan_b",
                            routeShape,
                            days,
                            ordered,
                            stayAnchors,
                            pool,
                            selectedQuote,
                            1));
        }
        return plans;
    }

    private String routeShape(
            TravelRequirementDTO requirement, RentalQuoteOptionDTO selectedQuote) {
        String text =
                (requirement.getRouteStructure() == null ? "" : requirement.getRouteStructure())
                        + " "
                        + (requirement.getRouteMode() == null ? "" : requirement.getRouteMode());
        if (text.contains("不走回头")
                || text.contains("单向")
                || text.contains("一路")
                || text.contains("异地")) {
            return "ONEWAY";
        }
        if (text.contains("固定住宿") || text.contains("不换酒店") || text.contains("基地")) {
            return "BASE";
        }
        if (selectedQuote != null && Boolean.TRUE.equals(selectedQuote.getIsOneWay())) {
            return "ONEWAY";
        }
        if (selectedQuote != null
                && selectedQuote.getReturnMode() != null
                && (selectedQuote.getReturnMode().contains("异地")
                        || selectedQuote.getReturnMode().contains("ONE"))) {
            return "ONEWAY";
        }
        return "LOOP";
    }

    private List<AreaAnchorCandidate> orderScenicAnchors(
            String routeShape,
            int days,
            List<AreaAnchorCandidate> scenicAnchors,
            CandidatePool pool) {
        AreaAnchorCandidate origin = pool == null ? null : pool.getPickupAnchor();
        List<AreaAnchorCandidate> sorted =
                scenicAnchors.stream()
                        .sorted(Comparator.comparingDouble(anchor -> distanceFrom(origin, anchor)))
                        .toList();
        if (!"LOOP".equals(routeShape) || sorted.size() <= 2 || days <= 2) {
            return sorted;
        }
        List<AreaAnchorCandidate> ordered = new ArrayList<>();
        ordered.add(sorted.get(0));
        ordered.add(sorted.get(sorted.size() - 1));
        for (int index = 1; index < sorted.size() - 1; index++) {
            ordered.add(sorted.get(index));
        }
        return ordered;
    }

    private double distanceFrom(AreaAnchorCandidate origin, AreaAnchorCandidate anchor) {
        if (origin == null || anchor == null) {
            return 0;
        }
        double[] from = parseLocation(origin.getLocation());
        double[] to = parseLocation(anchor.getLocation());
        if (from == null || to == null) {
            return 0;
        }
        return com.sora.aitravel.service.route.GeoRouteCalculator.distanceKm(
                from[0], from[1], to[0], to[1]);
    }

    private MacroRoutePlan rulePlan(
            String id,
            String shape,
            int days,
            List<AreaAnchorCandidate> scenicAnchors,
            List<AreaAnchorCandidate> stayAnchors,
            CandidatePool pool,
            RentalQuoteOptionDTO selectedQuote,
            int offset) {
        List<MacroRouteDay> routeDays = new ArrayList<>();
        String previousStayId =
                selectedQuote != null && pool.getPickupAnchor() != null
                        ? pool.getPickupAnchor().getId()
                        : null;
        for (int index = 0; index < days; index++) {
            AreaAnchorCandidate focus = scenicAnchors.get((index + offset) % scenicAnchors.size());
            AreaAnchorCandidate stay = nearestStayAnchor(focus, stayAnchors);
            String startId =
                    index == 0
                            ? firstNonBlank(previousStayId, focus.getId())
                            : firstNonBlank(previousStayId, focus.getId());
            String stayId = stay == null ? focus.getId() : stay.getId();
            routeDays.add(
                    new MacroRouteDay(
                            index + 1,
                            startId,
                            List.of(focus.getId()),
                            focus.getId(),
                            stayId,
                            firstNonBlank(focus.getName(), focus.getArea()) + "轻松游",
                            "按候选区域和跨天住宿衔接生成"));
            previousStayId = stayId;
        }
        return new MacroRoutePlan(id, shape, routeDays, List.of(), "规则生成，减少 AI 调用");
    }

    private AreaAnchorCandidate nearestStayAnchor(
            AreaAnchorCandidate focus, List<AreaAnchorCandidate> stayAnchors) {
        if (focus == null || stayAnchors == null || stayAnchors.isEmpty()) {
            return null;
        }
        double[] focusLocation = parseLocation(focus.getLocation());
        if (focusLocation == null) {
            return stayAnchors.get(0);
        }
        return stayAnchors.stream()
                .filter(anchor -> parseLocation(anchor.getLocation()) != null)
                .min(
                        java.util.Comparator.comparingDouble(
                                anchor -> {
                                    double[] location = parseLocation(anchor.getLocation());
                                    return com.sora.aitravel.service.route.GeoRouteCalculator
                                            .distanceKm(
                                                    focusLocation[0],
                                                    focusLocation[1],
                                                    location[0],
                                                    location[1]);
                                }))
                .orElse(stayAnchors.get(0));
    }

    private double[] parseLocation(String location) {
        return com.sora.aitravel.service.route.GeoRouteCalculator.parseLocation(location);
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private void normalizeHardRouteContracts(
            CandidatePool pool, RentalQuoteOptionDTO selectedQuote, List<MacroRoutePlan> plans) {
        if (plans == null || plans.isEmpty()) {
            return;
        }
        boolean rentalEnabled = selectedQuote != null;
        String pickupId =
                pool == null || pool.getPickupAnchor() == null
                        ? null
                        : pool.getPickupAnchor().getId();
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
            days.add(
                    new MacroRouteDay(
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
            lines.add(
                    "- id="
                            + anchor.getId()
                            + "；role="
                            + anchor.getRole()
                            + "；name="
                            + anchor.getName()
                            + "；city="
                            + anchor.getCity()
                            + "；area="
                            + anchor.getArea());
        }
        return String.join("\n", lines);
    }

    private List<AreaAnchorCandidate> selectedMacroAnchors(CandidatePool pool) {
        List<AreaAnchorCandidate> all =
                pool.getAreaAnchors() == null ? List.of() : pool.getAreaAnchors();
        List<AreaAnchorCandidate> result = new ArrayList<>();
        addIfPresent(result, pool.getPickupAnchor());
        addRole(result, all, "SCENIC_CLUSTER", 24);
        addRole(result, all, "STAY_AREA", 12);
        return result;
    }

    private void addRole(
            List<AreaAnchorCandidate> result,
            List<AreaAnchorCandidate> all,
            String role,
            int limit) {
        all.stream()
                .filter(anchor -> role.equals(anchor.getRole()))
                .filter(
                        anchor ->
                                result.stream()
                                        .noneMatch(
                                                existing ->
                                                        existing.getId().equals(anchor.getId())))
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
            node.forEach(
                    item -> {
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
