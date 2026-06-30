package com.sora.aitravel.workflow.generate;

import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.CITY_PROFILE;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.DAY_CONTEXTS;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.DAY_SKELETONS;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.RENTAL_TRIP_CONTEXT;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.REQUIREMENT;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.REVISION_TEXT;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.SELECTED_QUOTE;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.dto.model.RentalQuoteOptionDTO;
import com.sora.aitravel.dto.model.RentalTripContextDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.workflow.generate.state.TripGraphStateCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 构建每一天生成行程所需的上下文。 */
@Slf4j
@Component
public class DayContextBuildNode {

    public void execute(GenerateWorkflowContext context) {
        if (context.getDaySkeletons() == null || context.getDaySkeletons().isEmpty()) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "缺少逐日行程骨架，无法生成单日行程");
        }
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

    public Map<String, Object> execute(OverAllState state) {
        List<DaySkeleton> daySkeletons = TripGraphStateCodec.optionalList(state, DAY_SKELETONS, DaySkeleton.class);
        if (daySkeletons.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "缺少逐日行程骨架，无法生成单日行程");
        }
        TravelRequirementDTO requirement = TripGraphStateCodec.required(state, REQUIREMENT, TravelRequirementDTO.class);
        RentalQuoteOptionDTO selectedQuote =
                TripGraphStateCodec.optional(state, SELECTED_QUOTE, RentalQuoteOptionDTO.class).orElse(null);
        RentalTripContextDTO rentalTripContext =
                TripGraphStateCodec.optional(state, RENTAL_TRIP_CONTEXT, RentalTripContextDTO.class).orElse(null);
        CityProfile cityProfile = TripGraphStateCodec.optional(state, CITY_PROFILE, CityProfile.class).orElse(null);
        String revisionText = TripGraphStateCodec.optional(state, REVISION_TEXT, String.class).orElse(null);

        List<DayContext> dayContexts = new ArrayList<>();
        List<String> usedPlaces = new ArrayList<>();
        String hotelArea = resolveHotelArea(cityProfile, requirement);

        for (DaySkeleton skeleton : daySkeletons) {
            dayContexts.add(
                    new DayContext(
                            skeleton.getDay(),
                            skeleton,
                            List.copyOf(usedPlaces),
                            hotelArea,
                            requirement.getPace(),
                            selectedQuote != null,
                            rentalInstruction(selectedQuote, rentalTripContext),
                            rentalTripContext == null ? null : rentalTripContext.getRouteStructure(),
                            rentalTripContext == null ? null : rentalTripContext.getDailyDrivingLimit(),
                            revisionText));
            usedPlaces.add(skeleton.targetArea());
        }

        log.info("节点[day-context-build]：已构建每天上下文，count={}", dayContexts.size());
        return TripGraphStateCodec.patch(DAY_CONTEXTS, dayContexts);
    }

    private String resolveHotelArea(GenerateWorkflowContext context) {
        return resolveHotelArea(context.getCityProfile(), context.getRequirement());
    }

    private String resolveHotelArea(CityProfile profile, TravelRequirementDTO requirement) {
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
        if (requirement != null) {
            return firstNonBlank(
                    requirement.getDestination(),
                    requirement.getRouteRegion());
        }
        return null;
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private String rentalInstruction(GenerateWorkflowContext context) {
        return rentalInstruction(context.getSelectedQuote(), context.getRentalTripContext());
    }

    private String rentalInstruction(
            RentalQuoteOptionDTO selectedQuote, RentalTripContextDTO rentalTripContext) {
        if (selectedQuote == null || rentalTripContext == null) {
            return null;
        }
        String vehicle =
                firstNonBlank(
                        selectedQuote.getDisplayName(),
                        selectedQuote.getGroupName());
        String pickup =
                rentalTripContext.getPickupPlan() == null
                        ? null
                        : rentalTripContext.getPickupPlan().getDisplayText();
        String arrival =
                rentalTripContext.getArrivalPoint() == null
                        ? null
                        : rentalTripContext.getArrivalPoint().getName();
        return "本次为租车自驾行程，已选车辆："
                + firstNonBlank(vehicle, "租车套餐")
                + "；到达/接车点："
                + firstNonBlank(arrival, "目的地到达点")
                + "；接车安排："
                + firstNonBlank(pickup, "送车接人后开始自驾")
                + "；游玩范围："
                + firstNonBlank(rentalTripContext.getRouteStructure(), "城市+周边")
                + "；驾驶强度："
                + firstNonBlank(rentalTripContext.getDailyDrivingLimit(), "近郊自驾（单日累计约2-4小时）")
                + "。选点应优先考虑自驾顺路、停车便利、城市周边自然/古镇等有车更方便到达的地点。";
    }
}
