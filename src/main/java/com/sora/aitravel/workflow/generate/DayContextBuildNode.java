package com.sora.aitravel.workflow.generate;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 构建每一天生成行程所需的上下文。 */
@Slf4j
@Component
public class DayContextBuildNode {

    public void execute(GenerateWorkflowContext context) {
        List<DayContext> dayContexts = new ArrayList<>();
        List<String> usedPlaces = new ArrayList<>();
        String hotelArea = resolveHotelArea(context);

        for (DaySkeleton skeleton : context.getDaySkeletons()) {
            dayContexts.add(
                    new DayContext(
                            skeleton.getDay(),
                            skeleton,
                            List.copyOf(usedPlaces),
                            hotelArea,
                            context.getRequirement().getPace()));
            usedPlaces.add(skeleton.targetArea());
        }

        context.setDayContexts(dayContexts);
        log.info("节点[day-context-build]：已构建每天上下文，count={}", dayContexts.size());
    }

    private String resolveHotelArea(GenerateWorkflowContext context) {
        CityProfile profile = context.getCityProfile();
        if (profile != null
                && profile.hotelCandidates() != null
                && !profile.hotelCandidates().isEmpty()) {
            PoiCandidate hotel = profile.hotelCandidates().get(0);
            return firstNonBlank(hotel.getName(), hotel.getArea());
        }
        if (profile != null && profile.getPopularAreas() != null && !profile.getPopularAreas().isEmpty()) {
            return profile.getPopularAreas().get(0);
        }
        if (context.getRequirement() != null) {
            return firstNonBlank(
                    context.getRequirement().getDestination(),
                    context.getRequirement().getRouteRegion());
        }
        return null;
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
}
