package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.dto.model.TravelModeDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.workflow.WorkflowNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 旅行交通方式判断节点。
 *
 * <p>当前使用轻量规则判断是否偏自驾，后续可替换为“规则 + 高德路线 + AI 评估”的正式实现。
 */
@Component
public class TravelModeDecisionNode implements WorkflowNode<GenerateWorkflowContext> {

    @Override
    public void execute(GenerateWorkflowContext context) {
        TravelRequirementDTO requirement = context.getRequest().requirement();
        List<String> preferences =
                requirement.preferences() == null ? List.of() : requirement.preferences();

        boolean selfDrive =
                preferences.stream()
                        .anyMatch(
                                item ->
                                        item.contains("自驾")
                                                || item.contains("租车")
                                                || item.contains("周边")
                                                || item.contains("亲子"));

        List<String> tips = new ArrayList<>();
        if (selfDrive) {
            tips.add("优先把距离较远或分散的景点安排在同一天，减少往返绕路。");
            tips.add("市中心热门商圈停车压力较大，可混合使用地铁或打车。");
        } else {
            tips.add("优先按商圈和地铁动线组织行程，减少跨城区移动。");
            tips.add("如临时增加周边景点，可再切换为自驾增强方案。");
        }

        TravelModeDTO travelMode =
                new TravelModeDTO(
                        selfDrive ? "SELF_DRIVE" : "PUBLIC_TRANSIT",
                        selfDrive,
                        selfDrive ? "用户偏好中包含自驾、租车、周边或亲子等关键词。" : "当前需求更适合公共交通或城市内短途出行。",
                        tips);

        context.setRecommendationContext(
                new com.sora.aitravel.dto.model.RecommendationContextDTO(
                        List.of(),
                        List.of(),
                        List.of(),
                        new com.sora.aitravel.dto.model.TransportPlanDTO(
                                travelMode, null, null, tips)));
    }
}
