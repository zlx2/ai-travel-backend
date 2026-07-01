package com.sora.aitravel.workflow.generate;

import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.CANDIDATE_POOL;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.DAY_SKELETONS;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.REQUIREMENT;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.SELECTED_QUOTE;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.dto.model.RentalQuoteOptionDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.model.AreaAnchorCandidate;
import com.sora.aitravel.model.AreaAnchorSnapshot;
import com.sora.aitravel.model.CandidatePool;
import com.sora.aitravel.model.DaySkeleton;
import com.sora.aitravel.model.MacroRouteDay;
import com.sora.aitravel.model.MacroRoutePlan;
import com.sora.aitravel.service.route.GeoRouteCalculator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Generates macro route plans, annotates with AMAP facts, selects the best plan, and produces
 * DaySkeletons.
 */
@Slf4j
@Component
public class MacroRoutePrepareNode {

    public Map<String, Object> execute(OverAllState state) {
        CandidatePool pool =
                TripGraphStateCodec.required(state, CANDIDATE_POOL, CandidatePool.class);
        TravelRequirementDTO requirement =
                TripGraphStateCodec.required(state, REQUIREMENT, TravelRequirementDTO.class);
        RentalQuoteOptionDTO selectedQuote =
                TripGraphStateCodec.optional(state, SELECTED_QUOTE, RentalQuoteOptionDTO.class)
                        .orElse(null);
        List<DaySkeleton> skeletons = buildSkeletons(pool, requirement, selectedQuote);
        return TripGraphStateCodec.patch(DAY_SKELETONS, skeletons);
    }

    private List<DaySkeleton> buildSkeletons(
            CandidatePool pool,
            TravelRequirementDTO requirement,
            RentalQuoteOptionDTO selectedQuote) {
        List<AreaAnchorCandidate> macroAnchors = selectedMacroAnchors(pool);
        log.info("节点[macro-route-prepare]：规则生成宏观路线，anchors={}", macroAnchors.size());
        List<MacroRoutePlan> plans = generateRulePlans(macroAnchors, requirement, selectedQuote);
        log.info("节点[macro-route-prepare]：已生成路线骨架候选，plans={}", plans.size());

        Map<String, AreaAnchorCandidate> anchors = anchorMap(pool);
        plans.forEach(plan -> canonicalizePlan(plan, anchors));

        MacroRoutePlan selected = selectBestPlan(plans);
        validateHandoff(selectedQuote, selected, anchors);

        List<DaySkeleton> skeletons = buildDaySkeletons(selected, anchors, requirement);
        log.info("节点[macro-route-prepare]：产出 DaySkeletons，count={}", skeletons.size());
        return skeletons;
    }

    private List<MacroRoutePlan> generateRulePlans(
            List<AreaAnchorCandidate> anchors,
            TravelRequirementDTO requirement,
            RentalQuoteOptionDTO selectedQuote) {
        int days = value(requirement.getDays(), 1);
        List<AreaAnchorCandidate> scenicAnchors =
                anchors.stream().filter(a -> "SCENIC_CLUSTER".equals(a.getRole())).toList();
        List<AreaAnchorCandidate> stayAnchors =
                anchors.stream().filter(a -> "STAY_AREA".equals(a.getRole())).toList();
        if (scenicAnchors.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "缺少可用于路线骨架的景点区域候选");
        }
        String routeShape = routeShape(requirement, selectedQuote);
        List<AreaAnchorCandidate> ordered =
                orderScenicAnchors(routeShape, days, scenicAnchors, anchors);
        List<MacroRoutePlan> plans = new ArrayList<>();
        plans.add(
                rulePlan(
                        "plan_a",
                        routeShape,
                        days,
                        ordered,
                        stayAnchors,
                        anchors,
                        selectedQuote,
                        0));
        if (scenicAnchors.size() > 1) {
            plans.add(
                    rulePlan(
                            "plan_b",
                            routeShape,
                            days,
                            ordered,
                            stayAnchors,
                            anchors,
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
            List<AreaAnchorCandidate> allAnchors) {
        AreaAnchorCandidate origin = findPickupAnchor(allAnchors);
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

    private MacroRoutePlan rulePlan(
            String id,
            String shape,
            int days,
            List<AreaAnchorCandidate> scenicAnchors,
            List<AreaAnchorCandidate> stayAnchors,
            List<AreaAnchorCandidate> allAnchors,
            RentalQuoteOptionDTO selectedQuote,
            int offset) {
        List<MacroRouteDay> routeDays = new ArrayList<>();
        AreaAnchorCandidate pickup = findPickupAnchor(allAnchors);
        String previousStayId = selectedQuote != null && pickup != null ? pickup.getId() : null;
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

    private AreaAnchorCandidate findPickupAnchor(List<AreaAnchorCandidate> anchors) {
        return anchors.stream().filter(a -> "PICKUP".equals(a.getRole())).findFirst().orElse(null);
    }

    private AreaAnchorCandidate nearestStayAnchor(
            AreaAnchorCandidate focus, List<AreaAnchorCandidate> stayAnchors) {
        if (focus == null || stayAnchors == null || stayAnchors.isEmpty()) {
            return null;
        }
        double[] focusLocation = GeoRouteCalculator.parseLocation(focus.getLocation());
        if (focusLocation == null) {
            return stayAnchors.get(0);
        }
        return stayAnchors.stream()
                .filter(anchor -> GeoRouteCalculator.parseLocation(anchor.getLocation()) != null)
                .min(
                        Comparator.comparingDouble(
                                anchor -> {
                                    double[] loc =
                                            GeoRouteCalculator.parseLocation(anchor.getLocation());
                                    return GeoRouteCalculator.distanceKm(
                                            focusLocation[0], focusLocation[1], loc[0], loc[1]);
                                }))
                .orElse(stayAnchors.get(0));
    }

    private double distanceFrom(AreaAnchorCandidate origin, AreaAnchorCandidate anchor) {
        if (origin == null || anchor == null) {
            return 0;
        }
        double[] from = GeoRouteCalculator.parseLocation(origin.getLocation());
        double[] to = GeoRouteCalculator.parseLocation(anchor.getLocation());
        if (from == null || to == null) {
            return 0;
        }
        return GeoRouteCalculator.distanceKm(from[0], from[1], to[0], to[1]);
    }

    private MacroRoutePlan selectBestPlan(List<MacroRoutePlan> plans) {
        return plans.stream()
                .filter(plan -> plan.getDays() != null && !plan.getDays().isEmpty())
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_GENERATE_ERROR, "缺少有效路线方案"));
    }

    private void validateHandoff(
            RentalQuoteOptionDTO selectedQuote,
            MacroRoutePlan plan,
            Map<String, AreaAnchorCandidate> anchors) {
        if (selectedQuote != null && !plan.getDays().isEmpty()) {
            AreaAnchorCandidate firstStart = anchors.get(plan.getDays().get(0).getStartAreaId());
            if (firstStart == null || !"PICKUP".equals(firstStart.getRole())) {
                throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "租车行程第 1 天必须从取车区域开始");
            }
        }
        for (int index = 1; index < plan.getDays().size(); index++) {
            MacroRouteDay previous = plan.getDays().get(index - 1);
            MacroRouteDay current = plan.getDays().get(index);
            if (!previous.getStayAreaId().equals(current.getStartAreaId())) {
                throw new BusinessException(
                        ErrorCode.AI_GENERATE_ERROR,
                        "跨天衔接不一致：Day "
                                + previous.getDay()
                                + " 住宿区域必须作为 Day "
                                + current.getDay()
                                + " 出发区域");
            }
        }
    }

    private List<DaySkeleton> buildDaySkeletons(
            MacroRoutePlan plan,
            Map<String, AreaAnchorCandidate> anchors,
            TravelRequirementDTO requirement) {
        if (plan.getDays() == null || plan.getDays().size() != requirement.getDays()) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "路线骨架天数不完整");
        }
        for (MacroRouteDay day : plan.getDays()) {
            requireAnchor(anchors, day.getStartAreaId(), "startAreaId");
            requireAnchor(anchors, day.getEndAreaId(), "endAreaId");
            requireAnchor(anchors, day.getStayAreaId(), "stayAreaId");
            if (day.getFocusAreaIds() == null || day.getFocusAreaIds().isEmpty()) {
                throw new BusinessException(
                        ErrorCode.AI_GENERATE_ERROR, "路线骨架缺少 focusAreaIds，day=" + day.getDay());
            }
            day.getFocusAreaIds().forEach(id -> requireAnchor(anchors, id, "focusAreaIds"));
        }
        return plan.getDays().stream()
                .map(
                        day -> {
                            AreaAnchorCandidate focus = anchors.get(day.getFocusAreaIds().get(0));
                            DaySkeleton skeleton = new DaySkeleton();
                            skeleton.setDay(day.getDay());
                            skeleton.setTheme(
                                    firstNonBlank(day.getTheme(), focus.getName() + "慢游"));
                            skeleton.setTargetArea(focus.getName());
                            skeleton.setIntensity(requirement.getPace());
                            skeleton.setStartAreaId(day.getStartAreaId());
                            skeleton.setFocusAreaId(day.getFocusAreaIds().get(0));
                            skeleton.setEndAreaId(day.getEndAreaId());
                            skeleton.setStayAreaId(day.getStayAreaId());
                            skeleton.setStartArea(snapshot(anchors.get(day.getStartAreaId())));
                            skeleton.setFocusArea(snapshot(focus));
                            skeleton.setEndArea(snapshot(anchors.get(day.getEndAreaId())));
                            skeleton.setStayArea(snapshot(anchors.get(day.getStayAreaId())));
                            return skeleton;
                        })
                .toList();
    }

    private AreaAnchorSnapshot snapshot(AreaAnchorCandidate anchor) {
        return new AreaAnchorSnapshot(
                anchor.getId(),
                anchor.getName(),
                anchor.getRole(),
                anchor.getCity(),
                anchor.getArea(),
                anchor.getAddress(),
                anchor.getLocation());
    }

    private Map<String, AreaAnchorCandidate> anchorMap(CandidatePool pool) {
        Map<String, AreaAnchorCandidate> map = new HashMap<>();
        if (pool != null && pool.getAreaAnchors() != null) {
            pool.getAreaAnchors().forEach(anchor -> map.put(anchor.getId(), anchor));
        }
        return map;
    }

    private void requireAnchor(Map<String, AreaAnchorCandidate> anchors, String id, String field) {
        if (id == null || id.isBlank()) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "路线骨架缺少区域引用：" + field);
        }
        AreaAnchorCandidate exact = anchors.get(id);
        if (usable(exact)) {
            return;
        }
        List<AreaAnchorCandidate> matches =
                anchors.values().stream()
                        .filter(MacroRoutePrepareNode::usable)
                        .filter(
                                anchor ->
                                        id.equals(anchor.getSourcePoiId())
                                                || id.equals(anchor.getName())
                                                || anchor.getId().endsWith("_" + id))
                        .toList();
        if (matches.size() == 1) {
            return;
        }
        if (matches.size() > 1) {
            throw new BusinessException(
                    ErrorCode.AI_GENERATE_ERROR, "路线骨架区域引用不唯一：" + field + "=" + id);
        }
        throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "路线骨架引用无效区域：" + field + "=" + id);
    }

    private void canonicalizePlan(MacroRoutePlan plan, Map<String, AreaAnchorCandidate> anchors) {
        if (plan == null || plan.getDays() == null) {
            return;
        }
        for (MacroRouteDay day : plan.getDays()) {
            day.setStartAreaId(canonicalId(anchors, day.getStartAreaId(), "startAreaId"));
            day.setEndAreaId(canonicalId(anchors, day.getEndAreaId(), "endAreaId"));
            day.setStayAreaId(canonicalId(anchors, day.getStayAreaId(), "stayAreaId"));
            if (day.getFocusAreaIds() != null) {
                day.setFocusAreaIds(
                        day.getFocusAreaIds().stream()
                                .map(id -> canonicalId(anchors, id, "focusAreaIds"))
                                .toList());
            }
        }
    }

    private String canonicalId(
            Map<String, AreaAnchorCandidate> anchors, String rawId, String field) {
        if (rawId == null || rawId.isBlank()) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "路线骨架缺少区域引用：" + field);
        }
        AreaAnchorCandidate exact = anchors.get(rawId);
        if (usable(exact)) {
            return exact.getId();
        }
        return anchors.values().stream()
                .filter(MacroRoutePrepareNode::usable)
                .filter(
                        anchor ->
                                rawId.equals(anchor.getSourcePoiId())
                                        || rawId.equals(anchor.getName())
                                        || anchor.getId().endsWith("_" + rawId))
                .findFirst()
                .map(AreaAnchorCandidate::getId)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.AI_GENERATE_ERROR,
                                        "路线骨架引用无效区域：" + field + "=" + rawId));
    }

    private static boolean usable(AreaAnchorCandidate anchor) {
        return anchor != null && anchor.getLocation() != null && !anchor.getLocation().isBlank();
    }

    private List<AreaAnchorCandidate> selectedMacroAnchors(CandidatePool pool) {
        if (pool == null || pool.getAreaAnchors() == null || pool.getAreaAnchors().isEmpty()) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "缺少可用于路线骨架的区域候选");
        }
        List<AreaAnchorCandidate> all = pool.getAreaAnchors();
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

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private int value(Integer value, int fallback) {
        return value == null ? fallback : value;
    }
}
