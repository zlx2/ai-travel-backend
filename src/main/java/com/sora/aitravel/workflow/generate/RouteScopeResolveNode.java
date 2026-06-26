package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.dto.model.TravelRequirementDTO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RouteScopeResolveNode {

    private final RegionRoutePresetProperties regionRoutePresetProperties;

    public void execute(GenerateWorkflowContext context) {
        TravelRequirementDTO requirement = context.getRequirement();

        if (requirement.getRouteCities() != null && !requirement.getRouteCities().isEmpty()) {
            fillFromRouteCities(requirement);
            log.info(
                    "节点[route-scope-resolve]：destination={}, routeRegion={}, routeCities={}, source=USER",
                    requirement.getDestination(),
                    requirement.getRouteRegion(),
                    requirement.getRouteCities());
            return;
        }

        String destination = requirement.getDestination();
        if (destination == null || destination.isBlank()) {
            log.warn("节点[route-scope-resolve]：destination 为空，无法解析路线范围");
            return;
        }

        RegionRoutePresetProperties.RegionPresetGroup group =
                regionRoutePresetProperties.matchGroup(destination);
        if (group != null) {
            applyPreset(requirement, group, destination);
            log.info(
                    "节点[route-scope-resolve]：destination={}, routeRegion={}, routeCities={}, source=PRESET",
                    destination,
                    requirement.getRouteRegion(),
                    requirement.getRouteCities());
            return;
        }

        fallbackSingleCity(requirement, destination);
        log.info(
                "节点[route-scope-resolve]：destination={}, routeRegion={}, routeCities={}, source=CITY_FALLBACK",
                destination,
                requirement.getRouteRegion(),
                requirement.getRouteCities());
    }

    private void fillFromRouteCities(TravelRequirementDTO requirement) {
        if (requirement.getRouteRegion() == null || requirement.getRouteRegion().isBlank()) {
            requirement.setRouteRegion(requirement.getDestination());
        }
        if (requirement.getRouteMode() == null || requirement.getRouteMode().isBlank()) {
            requirement.setRouteMode(
                    requirement.getRouteCities().size() > 1 ? "REGION" : "CITY");
        }
        if (requirement.getRouteStructure() == null || requirement.getRouteStructure().isBlank()) {
            requirement.setRouteStructure(
                    requirement.getRouteCities().size() > 1 ? "MULTI_CITY" : "SINGLE_CITY");
        }
    }

    private void applyPreset(
            TravelRequirementDTO requirement,
            RegionRoutePresetProperties.RegionPresetGroup group,
            String destination) {
        int days =
                requirement.getDays() != null ? requirement.getDays() : 3;
        RegionRoutePresetProperties.RoutePreset preset =
                regionRoutePresetProperties.selectPreset(group, days, requirement.getPreferences());
        String standardName =
                group.getStandardName() != null ? group.getStandardName() : destination;

        if (preset != null && preset.getCities() != null && !preset.getCities().isEmpty()) {
            requirement.setRouteRegion(standardName);
            requirement.setRouteCities(preset.getCities());
            requirement.setRouteMode("REGION");
            requirement.setRouteStructure(
                    preset.getCities().size() > 1 ? "MULTI_CITY" : "SINGLE_CITY");
        } else {
            fallbackSingleCity(requirement, destination);
        }
    }

    private void fallbackSingleCity(TravelRequirementDTO requirement, String destination) {
        requirement.setRouteRegion(destination);
        requirement.setRouteCities(List.of(destination));
        requirement.setRouteMode("CITY");
        requirement.setRouteStructure("SINGLE_CITY");
    }
}
