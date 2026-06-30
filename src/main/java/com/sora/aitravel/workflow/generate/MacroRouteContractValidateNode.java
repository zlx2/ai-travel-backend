package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Hard contract validation for the selected macro route. */
@Slf4j
@Component
public class MacroRouteContractValidateNode {
    public void execute(GenerateWorkflowContext context) {
        MacroRoutePlan plan = selectedPlan(context);
        Map<String, AreaAnchorCandidate> anchors = anchorMap(context.getCandidatePool());
        AreaAnchorResolver.canonicalizePlan(plan, anchors);
        if (plan.getDays() == null || plan.getDays().size() != context.getRequirement().getDays()) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "路线骨架天数不完整");
        }
        for (MacroRouteDay day : plan.getDays()) {
            requireAnchor(anchors, day.getStartAreaId(), "startAreaId");
            requireAnchor(anchors, day.getEndAreaId(), "endAreaId");
            requireAnchor(anchors, day.getStayAreaId(), "stayAreaId");
            if (day.getFocusAreaIds() == null || day.getFocusAreaIds().isEmpty()) {
                throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "路线骨架缺少 focusAreaIds，day=" + day.getDay());
            }
            day.getFocusAreaIds().forEach(id -> requireAnchor(anchors, id, "focusAreaIds"));
        }
        validateDailyHandoff(context, plan, anchors);
        applySkeletons(context, plan, anchors);
        log.info("节点[macro-route-contract-validate]：路线骨架合同校验通过，plan={}", plan.getId());
    }

    private MacroRoutePlan selectedPlan(GenerateWorkflowContext context) {
        RouteCriticResult critic = context.getRouteCriticResult();
        if (critic != null && critic.getRevisedPlan() != null) {
            return critic.getRevisedPlan();
        }
        String selectedId = critic == null ? null : critic.getSelectedPlanId();
        return context.getMacroRoutePlans().stream()
                .filter(plan -> selectedId == null || plan.getId().equals(selectedId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_GENERATE_ERROR, "路线审稿未选中有效方案"));
    }

    private void applySkeletons(
            GenerateWorkflowContext context, MacroRoutePlan plan, Map<String, AreaAnchorCandidate> anchors) {
        List<DaySkeleton> skeletons = plan.getDays().stream()
                .map(day -> {
                    AreaAnchorCandidate focus = anchors.get(day.getFocusAreaIds().get(0));
                    DaySkeleton skeleton = new DaySkeleton();
                    skeleton.setDay(day.getDay());
                    skeleton.setTheme(firstNonBlank(day.getTheme(), focus.getName() + "慢游"));
                    skeleton.setTargetArea(focus.getName());
                    skeleton.setIntensity(context.getRequirement().getPace());
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
        context.setDaySkeletons(skeletons);
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
            GenerateWorkflowContext context, MacroRoutePlan plan, Map<String, AreaAnchorCandidate> anchors) {
        if (context.getSelectedQuote() != null && !plan.getDays().isEmpty()) {
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
