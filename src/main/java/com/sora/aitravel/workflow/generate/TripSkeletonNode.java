package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.dto.model.TravelRequirementDTO;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 生成整趟行程骨架，只确定每天主题、目标区域和强度。 */
@Slf4j
@Component
public class TripSkeletonNode {

    public void execute(GenerateWorkflowContext context) {
        TravelRequirementDTO requirement = context.getRequirement();
        List<DaySkeleton> skeletons = new ArrayList<>();
        for (int day = 1; day <= requirement.getDays(); day++) {
            skeletons.add(buildSkeleton(requirement, day));
        }
        context.setDaySkeletons(skeletons);
        log.info(
                "节点[trip-skeleton]：模拟生成整体行程骨架，days={}, skeletons={}",
                requirement.getDays(),
                skeletons);
    }

    private DaySkeleton buildSkeleton(TravelRequirementDTO requirement, int day) {
        String destination = displayDestination(requirement);
        boolean light = "LIGHT".equals(requirement.getPace());
        if (day == 1) {
            return new DaySkeleton(day, "抵达适应 + 城市初印象", destination + "核心商圈", "LOW");
        }
        if (day == requirement.getDays()) {
            return new DaySkeleton(day, "城市慢生活 + 返程预留", destination + "休闲街区", "LOW");
        }
        if (hasPreference(requirement, "自然")) {
            return new DaySkeleton(
                    day, "自然风光 + 轻户外体验", destination + "自然景区周边", light ? "LOW" : "MEDIUM");
        }
        if (hasPreference(requirement, "美食")) {
            return new DaySkeleton(
                    day, "城市文化 + 美食探索", destination + "老城与美食街区", light ? "LOW" : "MEDIUM");
        }
        return new DaySkeleton(
                day, "经典景点 + 顺路美食", destination + "热门游览区域", light ? "LOW" : "MEDIUM");
    }

    private boolean hasPreference(TravelRequirementDTO requirement, String keyword) {
        return requirement.getPreferences() != null
                && requirement.getPreferences().stream().anyMatch(item -> item.contains(keyword));
    }

    private String displayDestination(TravelRequirementDTO requirement) {
        if (requirement.getDestination() != null && !requirement.getDestination().isBlank()) {
            return requirement.getDestination();
        }
        if (requirement.getRouteRegion() != null && !requirement.getRouteRegion().isBlank()) {
            return requirement.getRouteRegion();
        }
        return String.join("-", requirement.getRouteCities());
    }
}
