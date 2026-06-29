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
                            context.getRequirement().getPace(),
                            context.getSelectedQuote() != null,
                            rentalInstruction(context),
                            context.getRentalTripContext() == null
                                    ? null
                                    : context.getRentalTripContext().getRouteStructure(),
                            context.getRentalTripContext() == null
                                    ? null
                                    : context.getRentalTripContext().getDailyDrivingLimit(),
                            context.getRevisionText()));
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
        if (profile != null
                && profile.getPopularAreas() != null
                && !profile.getPopularAreas().isEmpty()) {
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

    private String rentalInstruction(GenerateWorkflowContext context) {
        if (context.getSelectedQuote() == null || context.getRentalTripContext() == null) {
            return null;
        }
        String vehicle =
                firstNonBlank(
                        context.getSelectedQuote().getDisplayName(),
                        context.getSelectedQuote().getGroupName());
        String pickup =
                context.getRentalTripContext().getPickupPlan() == null
                        ? null
                        : context.getRentalTripContext().getPickupPlan().getDisplayText();
        String arrival =
                context.getRentalTripContext().getArrivalPoint() == null
                        ? null
                        : context.getRentalTripContext().getArrivalPoint().getName();
        return "本次为租车自驾行程，已选车辆："
                + firstNonBlank(vehicle, "租车套餐")
                + "；到达/接车点："
                + firstNonBlank(arrival, "目的地到达点")
                + "；接车安排："
                + firstNonBlank(pickup, "送车接人后开始自驾")
                + "；游玩范围："
                + firstNonBlank(context.getRentalTripContext().getRouteStructure(), "城市+周边")
                + "；驾驶强度："
                + firstNonBlank(context.getRentalTripContext().getDailyDrivingLimit(), "近郊自驾（单日累计约2-4小时）")
                + "。选点应优先考虑自驾顺路、停车便利、城市周边自然/古镇等有车更方便到达的地点。";
    }
}
