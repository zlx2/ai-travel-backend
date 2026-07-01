package com.sora.aitravel.workflow.generate;

import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.RENTAL_TRIP_CONTEXT;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.REQUEST;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.REQUIREMENT;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.SELECTED_QUOTE;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.config.RegionRoutePresetProperties;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.request.TripGenerateRequest;
import com.sora.aitravel.workflow.generate.state.TripGraphStateCodec;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RequirementPrepareNode {

    private final RegionRoutePresetProperties regionRoutePresetProperties;

    public Map<String, Object> execute(OverAllState state) {
        TripGenerateRequest request =
                TripGraphStateCodec.required(state, REQUEST, TripGenerateRequest.class);
        validate(request);
        TravelRequirementDTO requirement = request.getRequirement();
        resolve(requirement);
        return TripGraphStateCodec.patch(
                REQUIREMENT, requirement,
                SELECTED_QUOTE, request.getSelectedQuote(),
                RENTAL_TRIP_CONTEXT, request.getRentalTripContext());
    }

    private void validate(TripGenerateRequest request) {
        if (request == null || request.getRequirement() == null)
            throw new BusinessException(ErrorCode.PARAM_ERROR, "需求不能为空");
        TravelRequirementDTO r = request.getRequirement();
        if (r.getDeparture() == null || r.getDeparture().isBlank())
            throw new BusinessException(ErrorCode.PARAM_ERROR, "出发地不能为空");
        if (r.getDays() == null || r.getDays() < 1 || r.getDays() > 7)
            throw new BusinessException(ErrorCode.PARAM_ERROR, "天数需在1-7之间");
        boolean rental =
                "ROAD_TRIP".equals(r.getRouteMode())
                        || "LANDING_RENTAL_TRIP".equals(r.getRouteMode())
                        || "RENTAL_CAR".equals(r.getTransportMode())
                        || "SELF_DRIVE".equals(r.getTransportMode())
                        || "USER_REQUIRED".equals(r.getRentalIntent());
        if (rental && request.getSelectedQuote() == null)
            throw new BusinessException(ErrorCode.PARAM_ERROR, "租车需先确认报价");
        if ("ROAD_TRIP".equals(r.getRouteMode())) {
            boolean has =
                    (r.getRouteCities() != null && !r.getRouteCities().isEmpty())
                            || (r.getRouteRegion() != null && !r.getRouteRegion().isBlank())
                            || (r.getDestination() != null && !r.getDestination().isBlank());
            if (!has) throw new BusinessException(ErrorCode.PARAM_ERROR, "自驾需目的地/区域/城市");
            return;
        }
        if (r.getDestination() == null || r.getDestination().isBlank())
            throw new BusinessException(ErrorCode.PARAM_ERROR, "目的地不能为空");
    }

    private void resolve(TravelRequirementDTO r) {
        if (r.getRouteCities() != null && !r.getRouteCities().isEmpty()) {
            if (r.getRouteRegion() == null || r.getRouteRegion().isBlank())
                r.setRouteRegion(r.getDestination());
            if (r.getRouteMode() == null || r.getRouteMode().isBlank())
                r.setRouteMode(r.getRouteCities().size() > 1 ? "REGION" : "CITY");
            if (r.getRouteStructure() == null || r.getRouteStructure().isBlank())
                r.setRouteStructure(r.getRouteCities().size() > 1 ? "MULTI_CITY" : "SINGLE_CITY");
            return;
        }
        String dst = r.getDestination();
        if (dst == null || dst.isBlank()) return;
        var group = regionRoutePresetProperties.matchGroup(dst);
        if (group != null) {
            int days = r.getDays() != null ? r.getDays() : 3;
            var preset = regionRoutePresetProperties.selectPreset(group, days, r.getPreferences());
            String name = group.getStandardName() != null ? group.getStandardName() : dst;
            if (preset != null && preset.getCities() != null && !preset.getCities().isEmpty()) {
                r.setRouteRegion(name);
                r.setRouteCities(preset.getCities());
                r.setRouteMode("REGION");
                r.setRouteStructure(preset.getCities().size() > 1 ? "MULTI_CITY" : "SINGLE_CITY");
            } else {
                String fc =
                        group.getDefaultCity() != null && !group.getDefaultCity().isBlank()
                                ? group.getDefaultCity()
                                : dst;
                r.setRouteRegion(name);
                r.setRouteCities(List.of(fc));
                r.setRouteMode("REGION");
                r.setRouteStructure("SINGLE_CITY");
            }
            return;
        }
        r.setRouteRegion(dst);
        r.setRouteCities(List.of(dst));
        r.setRouteMode("CITY");
        r.setRouteStructure("SINGLE_CITY");
    }
}
