package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.dto.model.TravelRequirementDTO;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AiRouteCriticNodeTest {

    @Test
    @DisplayName("规则审稿选择第一个完整有效的宏观路线方案")
    void shouldSelectFirstCompleteMacroRoutePlan() {
        AiRouteCriticNode node = new AiRouteCriticNode();
        GenerateWorkflowContext context = context();

        node.execute(context);

        checkValue("选中方案", "plan_a", context.getRouteCriticResult().getSelectedPlanId());
        checkValue("规则分数", 88, context.getRouteCriticResult().getScore());
        checkValue("规则不改写路线骨架", null, context.getRouteCriticResult().getRevisedPlan());
    }

    private GenerateWorkflowContext context() {
        TravelRequirementDTO requirement = new TravelRequirementDTO();
        requirement.setDestination("成都");
        requirement.setDays(3);
        requirement.setPace("舒适");
        requirement.setPreferences(List.of("亲子"));

        GenerateWorkflowContext context = new GenerateWorkflowContext();
        context.setRequirement(requirement);
        context.setMacroRoutePlans(List.of(new MacroRoutePlan(
                "plan_a",
                "LOOP",
                List.of(
                        new MacroRouteDay(
                                1,
                                "pickup",
                                List.of("chengdu"),
                                "chengdu",
                                "stay_day_1",
                                "市区适应",
                                "先适应城市"),
                        new MacroRouteDay(
                                2,
                                "stay_day_1",
                                List.of("dujiangyan"),
                                "dujiangyan",
                                "stay_day_2",
                                "都江堰",
                                "游览水利工程"),
                        new MacroRouteDay(
                                3,
                                "stay_day_2",
                                List.of("qingchengshan"),
                                "chengdu",
                                "chengdu",
                                "青城山",
                                "返程前慢游")),
                List.of(),
                "原方案")));
        context.setMacroRouteFacts(List.of(new MacroRouteFact("plan_a", List.of(), 120, 80000, List.of())));
        return context;
    }

    private void checkValue(String fieldName, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new IllegalStateException(
                    fieldName + " 检查失败：预期值为“" + expected + "”，实际值为“" + actual + "”");
        }
    }
}
