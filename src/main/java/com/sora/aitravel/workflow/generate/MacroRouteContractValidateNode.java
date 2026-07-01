package com.sora.aitravel.workflow.generate;

import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.CANDIDATE_POOL;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.DAY_SKELETONS;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.MACRO_ROUTE_PLANS;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.REQUIREMENT;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.ROUTE_CRITIC_RESULT;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.SELECTED_QUOTE;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.dto.model.RentalQuoteOptionDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.workflow.generate.state.TripGraphStateCodec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Hard contract validation for the selected macro route. */
@Slf4j
@Component
public class MacroRouteContractValidateNode {

    public Map<String, Object> execute(OverAllState state) {
        List<DaySkeleton> skeletons =
                validateAndBuildSkeletons(
                        TripGraphStateCodec.optional(
                                        state, ROUTE_CRITIC_RESULT, RouteCriticResult.class)
                                .orElse(null),
                        TripGraphStateCodec.optionalList(
                                state, MACRO_ROUTE_PLANS, MacroRoutePlan.class),
                        TripGraphStateCodec.required(state, CANDIDATE_POOL, CandidatePool.class),
                        TripGraphStateCodec.required(
                                state, REQUIREMENT, TravelRequirementDTO.class),
                        TripGraphStateCodec.optional(
                                        state, SELECTED_QUOTE, RentalQuoteOptionDTO.class)
                                .orElse(null));
        return TripGraphStateCodec.patch(DAY_SKELETONS, skeletons);
    }

    private List<DaySkeleton> validateAndBuildSkeletons(
            RouteCriticResult critic,
            List<MacroRoutePlan> macroRoutePlans,
            CandidatePool candidatePool,
            TravelRequirementDTO requirement,
            RentalQuoteOptionDTO selectedQuote) {
        MacroRoutePlan plan = selectedPlan(critic, macroRoutePlans);
        Map<String, AreaAnchorCandidate> anchors = anchorMap(candidatePool);
        AreaAnchorResolver.canonicalizePlan(plan, anchors);
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
        validateDailyHandoff(selectedQuote, plan, anchors);
        List<DaySkeleton> skeletons = buildSkeletons(plan, anchors, requirement);
        log.info("节点[macro-route-contract-validate]：路线骨架合同校验通过，plan={}", plan.getId());
        return skeletons;
    }

    private MacroRoutePlan selectedPlan(
            RouteCriticResult critic, List<MacroRoutePlan> macroRoutePlans) {
        if (critic != null && critic.getRevisedPlan() != null) {
            return critic.getRevisedPlan();
        }
        String selectedId = critic == null ? null : critic.getSelectedPlanId();
        return macroRoutePlans.stream()
                .filter(plan -> selectedId == null || plan.getId().equals(selectedId))
                .findFirst()
                .orElseThrow(
                        () -> new BusinessException(ErrorCode.AI_GENERATE_ERROR, "路线审稿未选中有效方案"));
    }

    private List<DaySkeleton> buildSkeletons(
            MacroRoutePlan plan,
            Map<String, AreaAnchorCandidate> anchors,
            TravelRequirementDTO requirement) {
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
        AreaAnchorResolver.resolve(anchors, id, field);
    }

    private void validateDailyHandoff(
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

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
