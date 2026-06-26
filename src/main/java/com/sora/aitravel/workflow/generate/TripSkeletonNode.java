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
                "节点[trip-skeleton]：按需求生成整体行程骨架，days={}, skeletons={}",
                requirement.getDays(),
                skeletons);
    }

    private DaySkeleton buildSkeleton(TravelRequirementDTO requirement, int day) {
        String city = resolveCityForDay(requirement, day);
        boolean light = "LIGHT".equals(requirement.getPace());
        if (day == 1) {
            return new DaySkeleton(day, city + "城市开场", city + "核心城区", "LIGHT");
        }
        if (day == requirement.getDays()) {
            return new DaySkeleton(day, city + "慢游收束", city + "休闲街区", "LIGHT");
        }
        if (day == 2 && (hasPreference(requirement, "夜景") || hasPreference(requirement, "夜市"))) {
            return new DaySkeleton(
                    day, city + "夜色漫游", city + "夜间活跃区域", light ? "LIGHT" : "NORMAL");
        }
        if (hasPreference(requirement, "自然")) {
            return new DaySkeleton(
                    day, city + "自然风光", city + "自然景区周边", light ? "LIGHT" : "NORMAL");
        }
        if (hasPreference(requirement, "美食")) {
            return new DaySkeleton(
                    day, city + "美食探索", city + "老城与美食街区", light ? "LIGHT" : "NORMAL");
        }
        return new DaySkeleton(
                day, city + "经典景点", city + "热门游览区域", light ? "LIGHT" : "NORMAL");
    }

    private String resolveCityForDay(TravelRequirementDTO requirement, int day) {
        List<String> routeCities = requirement.getRouteCities();
        if (routeCities != null && !routeCities.isEmpty()) {
            int days = requirement.getDays() != null ? requirement.getDays() : 1;
            int cityIndex = (int) Math.floor((double) (day - 1) * routeCities.size() / days);
            if (cityIndex >= routeCities.size()) {
                cityIndex = routeCities.size() - 1;
            }
            return routeCities.get(cityIndex);
        }
        return displayDestination(requirement);
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
