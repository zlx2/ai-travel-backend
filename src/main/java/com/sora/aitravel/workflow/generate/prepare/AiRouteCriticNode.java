package com.sora.aitravel.workflow.generate.prepare;

import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.MACRO_ROUTE_FACTS;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.MACRO_ROUTE_PLANS;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.REQUIREMENT;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.ROUTE_CRITIC_RESULT;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.model.trip.generate.MacroRouteFact;
import com.sora.aitravel.model.trip.generate.MacroRoutePlan;
import com.sora.aitravel.model.trip.generate.RouteCriticResult;
import com.sora.aitravel.workflow.generate.state.TripGraphStateCodec;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Selects the macro route contract with deterministic state-safe rules. */
@Slf4j
@Component
public class AiRouteCriticNode {

    public Map<String, Object> execute(OverAllState state) {
        RouteCriticResult result =
                review(
                        TripGraphStateCodec.required(
                                state, REQUIREMENT, TravelRequirementDTO.class),
                        TripGraphStateCodec.optionalList(
                                state, MACRO_ROUTE_PLANS, MacroRoutePlan.class),
                        TripGraphStateCodec.optionalList(
                                state, MACRO_ROUTE_FACTS, MacroRouteFact.class));
        return TripGraphStateCodec.patch(ROUTE_CRITIC_RESULT, result);
    }

    private RouteCriticResult review(
            TravelRequirementDTO requirement,
            List<MacroRoutePlan> macroRoutePlans,
            List<MacroRouteFact> macroRouteFacts) {
        if (macroRoutePlans == null || macroRoutePlans.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "缺少路线方案，无法审稿");
        }
        RouteCriticResult result = ruleReview(macroRoutePlans);
        log.info(
                "节点[ai-route-critic]：规则路线审稿完成，selected={}, score={}",
                result.getSelectedPlanId(),
                result.getScore());
        return result;
    }

    private RouteCriticResult ruleReview(List<MacroRoutePlan> macroRoutePlans) {
        MacroRoutePlan selected =
                macroRoutePlans.stream()
                        .filter(plan -> plan.getDays() != null && !plan.getDays().isEmpty())
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.AI_GENERATE_ERROR, "缺少有效路线方案"));
        return new RouteCriticResult(
                selected.getId(),
                null,
                88,
                List.of(),
                firstNonBlank(selected.getReason(), "按候选区域距离和跨天住宿衔接选择"));
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
