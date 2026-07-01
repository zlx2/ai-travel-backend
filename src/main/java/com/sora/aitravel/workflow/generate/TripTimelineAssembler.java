package com.sora.aitravel.workflow.generate;

import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.DAY_SKELETONS;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.LOCKED_DAILY_PLANS;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.PREVIOUS_DAILY_PLANS;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.RANKED_DAY_DATA_PACKAGES;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.RENTAL_TRIP_CONTEXT;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.REQUIREMENT;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.SELECTED_QUOTE;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.sora.aitravel.dto.model.RentalQuoteOptionDTO;
import com.sora.aitravel.dto.model.RentalTripContextDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.model.TripPlanDTO;
import com.sora.aitravel.model.trip.generate.AreaAnchorSnapshot;
import com.sora.aitravel.model.trip.generate.DayDataPackage;
import com.sora.aitravel.model.trip.generate.DaySkeleton;
import com.sora.aitravel.model.trip.generate.PoiCandidate;
import com.sora.aitravel.workflow.generate.state.TripGraphStateCodec;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Builds the frontend timeline from backend-owned itinerary facts. */
@Component
public class TripTimelineAssembler {

    private static final String TYPE_DAY_START = "DAY_START";
    private static final String TYPE_TRANSFER = "TRANSFER";
    private static final String TYPE_RENTAL_PICKUP = "RENTAL_PICKUP";
    private static final String TYPE_CAR_RETURN_SERVICE = "CAR_RETURN_SERVICE";
    private static final String TYPE_LUNCH_AREA = "LUNCH_AREA";
    private static final String TYPE_DINNER_AREA = "DINNER_AREA";
    private static final String TYPE_STAY_AREA = "STAY_AREA";
    private static final String TYPE_SCENIC = "SCENIC";
    private static final int LUNCH_EARLIEST = 11 * 60 + 40;
    private static final int LUNCH_LATEST = 12 * 60 + 50;
    private static final int DINNER_EARLIEST = 17 * 60 + 40;
    private static final int DINNER_LATEST = 18 * 60 + 50;
    private static final int HOTEL_EARLIEST = 20 * 60;
    private static final int HOTEL_LATEST = 21 * 60;
    private static final int LAST_SPOT_END_LIMIT = 19 * 60 + 15;
    private static final double MAX_HOTEL_ANCHOR_DISTANCE_KM = 6.0;

    public Map<String, Object> execute(OverAllState state) {
        List<TripPlanDTO.DailyPlan> currentDays =
                TripGraphStateCodec.optionalList(
                        state, LOCKED_DAILY_PLANS, TripPlanDTO.DailyPlan.class);
        assemble(
                new TimelineInput(
                        TripGraphStateCodec.optionalList(
                                state, PREVIOUS_DAILY_PLANS, TripPlanDTO.DailyPlan.class),
                        currentDays,
                        TripGraphStateCodec.required(
                                state, REQUIREMENT, TravelRequirementDTO.class),
                        TripGraphStateCodec.optional(
                                        state, SELECTED_QUOTE, RentalQuoteOptionDTO.class)
                                .orElse(null),
                        TripGraphStateCodec.optional(
                                        state, RENTAL_TRIP_CONTEXT, RentalTripContextDTO.class)
                                .orElse(null),
                        TripGraphStateCodec.optionalList(state, DAY_SKELETONS, DaySkeleton.class),
                        TripGraphStateCodec.optionalList(
                                state, RANKED_DAY_DATA_PACKAGES, DayDataPackage.class)));
        return TripGraphStateCodec.patch(LOCKED_DAILY_PLANS, currentDays);
    }

    private void assemble(TimelineInput input) {
        assemble(input.getPreviousDays(), input.getCurrentDays(), input);
    }

    public void assemble(
            List<TripPlanDTO.DailyPlan> previousDays,
            List<TripPlanDTO.DailyPlan> currentDays,
            TimelineInput input) {
        if (currentDays == null || currentDays.isEmpty()) {
            return;
        }
        List<TripPlanDTO.DailyPlan> allDays = new ArrayList<>();
        if (previousDays != null) {
            allDays.addAll(previousDays);
        }
        allDays.addAll(currentDays);
        allDays.sort(Comparator.comparing(day -> value(day.getDay())));

        for (TripPlanDTO.DailyPlan day : currentDays) {
            TripPlanDTO.DailyPlan previousDay = findDay(allDays, value(day.getDay()) - 1);
            assembleDay(day, previousDay, input);
        }
    }

    private void assembleDay(
            TripPlanDTO.DailyPlan day, TripPlanDTO.DailyPlan previousDay, TimelineInput input) {
        List<TripPlanDTO.Spot> spots = orderedSpots(day);
        List<TripPlanDTO.Spot> daytimeSpots =
                spots.stream().filter(spot -> !isNightSpot(spot)).toList();
        List<TripPlanDTO.Spot> nightSpots = spots.stream().filter(this::isNightSpot).toList();
        TripPlanDTO.Spot firstSpot = spots.isEmpty() ? null : spots.get(0);
        TripPlanDTO.Anchor previousEnd = previousDay == null ? null : endAnchor(previousDay);
        TripPlanDTO.Anchor hotelAnchor = hotelAnchor(day, input);

        day.setStartAnchor(startAnchor(day, firstSpot, previousEnd, input));
        day.setEndAnchor(hotelAnchor);

        List<TripPlanDTO.TimelineNode> timeline = new ArrayList<>();
        TimelineClock clock = new TimelineClock(dayStartTime(day, input));
        int order = 1;

        if (value(day.getDay()) > 1 && previousEnd != null) {
            timeline.add(dayStartNode(order++, clock.time(), previousEnd, firstSpot));
            clock.move(20);
            if (firstSpot != null) {
                timeline.add(
                        transferNode(order++, clock.time(), previousEnd, firstSpot, day, input));
                clock.move(transferMinutes(previousEnd, firstSpot, day, input) + 10);
            }
        }

        if (value(day.getDay()) == 1 && hasRental(input)) {
            TripPlanDTO.TimelineNode pickup = pickupNode(order++, pickupStartTime(input), input);
            timeline.add(pickup);
            clock.reset(
                    Math.max(
                            clock.minutes(),
                            addMinutes(
                                    pickup.getStartTime(),
                                    value(pickup.getDurationMinutes(), 45))));
            if (firstSpot != null) {
                clock.move(transferMinutes(day.getStartAnchor(), firstSpot, day, input) + 10);
            }
        }

        int nextDaytimeIndex = 0;
        if (!daytimeSpots.isEmpty() && clock.minutes() < 11 * 60 + 20) {
            TripPlanDTO.Spot firstDaytimeSpot = daytimeSpots.get(0);
            int morningDuration = morningSpotDuration(clock.minutes(), firstDaytimeSpot);
            if (morningDuration >= 45
                    || canFitSpotBefore(clock.minutes(), firstDaytimeSpot, LUNCH_LATEST, input)) {
                timeline.add(
                        spotNode(
                                order++,
                                clock.time(),
                                firstDaytimeSpot,
                                startRouteSuggestion(previousEnd, day, firstDaytimeSpot, input),
                                morningDuration >= 45
                                        ? morningDuration
                                        : duration(firstDaytimeSpot)));
                clock.move(
                        (morningDuration >= 45 ? morningDuration : duration(firstDaytimeSpot))
                                + routeBuffer(input));
                nextDaytimeIndex = 1;
            }
        }

        TripPlanDTO.Spot lunchReference =
                referenceSpot(daytimeSpots, nextDaytimeIndex - 1, nextDaytimeIndex);
        timeline.add(
                mealNode(
                        order++,
                        lunchTime(clock.minutes()),
                        TYPE_LUNCH_AREA,
                        day,
                        input,
                        lunchReference));
        clock.reset(
                Math.max(
                        addMinutes(timeline.get(timeline.size() - 1).getStartTime(), 65),
                        13 * 60 + 20));

        for (int index = nextDaytimeIndex; index < daytimeSpots.size(); index++) {
            TripPlanDTO.Spot previous = index == 0 ? null : daytimeSpots.get(index - 1);
            TripPlanDTO.Spot spot = daytimeSpots.get(index);
            if (!canFitSpotBefore(clock.minutes(), spot, LAST_SPOT_END_LIMIT, input)) {
                break;
            }
            timeline.add(
                    spotNode(order++, clock.time(), spot, routeSuggestion(day, previous, spot)));
            clock.move(duration(spot) + routeBuffer(input));
        }

        TripPlanDTO.Spot dinnerReference = lastTimelineSpot(timeline, spots);
        timeline.add(
                mealNode(
                        order++,
                        dinnerTime(clock.minutes()),
                        TYPE_DINNER_AREA,
                        day,
                        input,
                        dinnerReference));
        clock.reset(
                Math.max(
                        addMinutes(timeline.get(timeline.size() - 1).getStartTime(), 80),
                        19 * 60 + 20));

        for (TripPlanDTO.Spot nightSpot : nightSpots) {
            if (!canFitSpotBefore(clock.minutes(), nightSpot, LAST_SPOT_END_LIMIT, input)) {
                break;
            }
            timeline.add(
                    spotNode(
                            order++,
                            clock.time(),
                            nightSpot,
                            routeSuggestion(day, null, nightSpot)));
            clock.move(duration(nightSpot) + routeBuffer(input));
        }

        TripPlanDTO.TimelineNode hotel =
                hotelNode(order++, hotelTime(clock.minutes()), day, hotelAnchor);
        timeline.add(hotel);

        if (isLastDay(day, input) && hasRental(input)) {
            int returnTime =
                    Math.min(
                            Math.max(addMinutes(hotel.getStartTime(), 35), 20 * 60 + 30),
                            21 * 60 + 30);
            timeline.add(returnNode(order++, formatTime(returnTime), input));
        }

        day.setTimeline(timeline);
    }

    private TripPlanDTO.TimelineNode dayStartNode(
            int order, String time, TripPlanDTO.Anchor previousEnd, TripPlanDTO.Spot firstSpot) {
        TripPlanDTO.TimelineNode node = compactNode(order, TYPE_DAY_START, time, "从酒店出发");
        node.setSubtitle(firstSpot == null ? "延续上一晚住宿区域" : "延续上一晚住宿区域，前往今日第一站");
        node.setDescription(node.getSubtitle());
        applyAnchor(node, previousEnd);
        node.setDurationMinutes(20);
        node.setDurationText("约20分钟");
        node.setSource("DAY_ANCHOR");
        node.setFromAnchor(previousEnd.getName());
        node.setToAnchor(firstSpot == null ? null : firstSpot.getName());
        node.setTags(List.of("出发", "酒店"));
        return withEndTime(node);
    }

    private TripPlanDTO.TimelineNode transferNode(
            int order,
            String time,
            TripPlanDTO.Anchor previousEnd,
            TripPlanDTO.Spot firstSpot,
            TripPlanDTO.DailyPlan day,
            TimelineInput input) {
        String to = firstSpot == null ? firstNonBlank(day.getCity(), "今日首站") : firstSpot.getName();
        TripPlanDTO.TimelineNode node = compactNode(order, TYPE_TRANSFER, time, "前往" + to);
        int minutes = transferMinutes(previousEnd, firstSpot, day, input);
        node.setSubtitle((hasRental(input) ? "自驾" : "交通") + "约" + readableMinutes(minutes));
        node.setDescription(node.getSubtitle());
        applyAnchor(node, previousEnd);
        node.setDurationMinutes(minutes);
        node.setDurationText("约" + readableMinutes(minutes));
        node.setTransportSuggestion(hasRental(input) ? "建议按实时路况出发。" : "建议按当天位置选择打车或公共交通。");
        node.setSource("DAY_ANCHOR");
        node.setFromAnchor(previousEnd.getName());
        node.setToAnchor(to);
        node.setTags(
                List.of(crossCity(previousEnd, day) ? "跨城" : "路程", hasRental(input) ? "自驾" : "交通"));
        return withEndTime(node);
    }

    private TripPlanDTO.TimelineNode pickupNode(int order, String time, TimelineInput input) {
        RentalTripContextDTO rental = input.getRentalTripContext();
        TripPlanDTO.TimelineNode node = compactNode(order, TYPE_RENTAL_PICKUP, time, "取车");
        String point =
                rental == null || rental.getArrivalPoint() == null
                        ? null
                        : rental.getArrivalPoint().getName();
        String store =
                rental == null || rental.getMatchedStore() == null
                        ? null
                        : rental.getMatchedStore().getDisplayName();
        node.setSubtitle(firstNonBlank(store, firstNonBlank(point, "到达点附近取车")));
        node.setDescription("完成验车后开始行程。");
        node.setArea(store);
        node.setAddress(point);
        if (rental != null && rental.getMatchedStore() != null) {
            node.setLng(decimal(rental.getMatchedStore().getLng()));
            node.setLat(decimal(rental.getMatchedStore().getLat()));
            node.setCity(rental.getMatchedStore().getCityName());
        } else if (input.getSelectedQuote() != null) {
            node.setLng(input.getSelectedQuote().getPickupLng());
            node.setLat(input.getSelectedQuote().getPickupLat());
            node.setArea(
                    firstNonBlank(input.getSelectedQuote().getPickupPoiName(), node.getArea()));
            node.setAddress(
                    firstNonBlank(input.getSelectedQuote().getPickupAddress(), node.getAddress()));
        }
        node.setCoordType("GCJ02");
        node.setDurationMinutes(45);
        node.setDurationText("约45分钟");
        node.setSource("RENTAL_CONTEXT");
        node.setTags(List.of("取车", "租车"));
        return withEndTime(node);
    }

    private TripPlanDTO.TimelineNode returnNode(int order, String time, TimelineInput input) {
        TripPlanDTO.TimelineNode node = compactNode(order, TYPE_CAR_RETURN_SERVICE, time, "上门取车");
        RentalTripContextDTO rental = input.getRentalTripContext();
        String point =
                rental == null
                        ? null
                        : firstNonBlank(
                                rental.getReturnPoint(),
                                rental.getArrivalPoint() == null
                                        ? null
                                        : rental.getArrivalPoint().getName());
        node.setSubtitle(firstNonBlank(point, "住宿区域附近交接"));
        node.setDescription("工作人员将在住宿区域附近上门取车，具体交接时间以下单后确认为准。");
        node.setDurationMinutes(30);
        node.setDurationText("约30分钟");
        node.setSource("RENTAL_CONTEXT");
        node.setTags(List.of("上门取车", "租车"));
        return withEndTime(node);
    }

    private TripPlanDTO.TimelineNode mealNode(
            int order,
            String time,
            String type,
            TripPlanDTO.DailyPlan day,
            TimelineInput input,
            TripPlanDTO.Spot routeReference) {
        TripPlanDTO.FoodSuggestion food = foodSuggestion(day, type, routeReference);
        String title = TYPE_LUNCH_AREA.equals(type) ? "午餐区域" : "晚餐区域";
        TripPlanDTO.TimelineNode node = compactNode(order, type, time, title);
        String area =
                firstNonBlank(
                        food == null ? null : food.getArea(),
                        firstNonBlank(day.getDiningArea(), "就近用餐"));
        node.setSubtitle(area + "附近");
        node.setDescription("在" + area + "附近安排用餐，作为区域推荐点展示。");
        node.setArea(area);
        if (food != null) {
            node.setCity(food.getCity());
            node.setAddress(food.getAddress());
            node.setLng(food.getLng());
            node.setLat(food.getLat());
            node.setCoordType(firstNonBlank(food.getCoordType(), "GCJ02"));
        }
        node.setDurationMinutes(TYPE_LUNCH_AREA.equals(type) ? 60 : 75);
        node.setDurationText(TYPE_LUNCH_AREA.equals(type) ? "约1小时" : "约1小时15分钟");
        int people =
                input.getRequirement() == null || input.getRequirement().getPeopleCount() == null
                        ? 1
                        : input.getRequirement().getPeopleCount();
        Integer averageCost = food == null ? null : food.getAverageCost();
        if (averageCost != null) {
            node.setEstimatedCost(averageCost * people);
            node.setCostText("约¥" + averageCost + "/人");
        }
        node.setSource(food == null ? "RULE_ESTIMATED" : food.getSource());
        node.setTags(List.of(title, "餐饮"));
        return withEndTime(node);
    }

    private TripPlanDTO.TimelineNode hotelNode(
            int order, String time, TripPlanDTO.DailyPlan day, TripPlanDTO.Anchor hotelAnchor) {
        TripPlanDTO.TimelineNode node = compactNode(order, TYPE_STAY_AREA, time, "住宿区域");
        node.setSubtitle("建议住在" + firstNonBlank(hotelAnchor.getArea(), hotelAnchor.getName()));
        node.setDescription("今晚建议住在该区域，方便休息并衔接下一天出发。");
        applyAnchor(node, hotelAnchor);
        node.setDurationMinutes(30);
        node.setDurationText("约30分钟");
        node.setSource("STAY_AREA");
        node.setTags(List.of("住宿区域", "休息"));
        return withEndTime(node);
    }

    private TripPlanDTO.TimelineNode spotNode(
            int order, String time, TripPlanDTO.Spot spot, String transportSuggestion) {
        return spotNode(order, time, spot, transportSuggestion, duration(spot));
    }

    private TripPlanDTO.TimelineNode spotNode(
            int order,
            String time,
            TripPlanDTO.Spot spot,
            String transportSuggestion,
            int durationMinutes) {
        TripPlanDTO.TimelineNode node = new TripPlanDTO.TimelineNode();
        node.setOrder(order);
        node.setType(firstNonBlank(spot.getType(), TYPE_SCENIC));
        node.setStartTime(time);
        node.setTitle(spot.getName());
        node.setSubtitle(firstNonBlank(spot.getArea(), spot.getAddress()));
        node.setDescription(spot.getReason());
        node.setCity(spot.getCity());
        node.setArea(spot.getArea());
        node.setAddress(spot.getAddress());
        node.setLng(firstNonNull(spot.getEntranceLng(), spot.getLng()));
        node.setLat(firstNonNull(spot.getEntranceLat(), spot.getLat()));
        node.setCoordType(firstNonBlank(spot.getCoordType(), "GCJ02"));
        node.setDurationMinutes(durationMinutes);
        node.setDurationText("约" + readableMinutes(durationMinutes));
        node.setTransportSuggestion(transportSuggestion);
        node.setEstimatedCost(spot.getTicketCost());
        node.setCostText(spot.getTicketCostText());
        node.setReason(spot.getReason());
        node.setTags(spot.getTags());
        node.setCompact(false);
        node.setSource(spot.getSource());
        node.setToOrder(spot.getOrder());
        return withEndTime(node);
    }

    private TripPlanDTO.TimelineNode compactNode(
            int order, String type, String time, String title) {
        TripPlanDTO.TimelineNode node = new TripPlanDTO.TimelineNode();
        node.setOrder(order);
        node.setType(type);
        node.setStartTime(time);
        node.setTitle(title);
        node.setCompact(true);
        return node;
    }

    private TripPlanDTO.TimelineNode withEndTime(TripPlanDTO.TimelineNode node) {
        if (node.getStartTime() != null && node.getDurationMinutes() != null) {
            node.setEndTime(formatTime(addMinutes(node.getStartTime(), node.getDurationMinutes())));
        }
        return node;
    }

    private TripPlanDTO.Anchor startAnchor(
            TripPlanDTO.DailyPlan day,
            TripPlanDTO.Spot firstSpot,
            TripPlanDTO.Anchor previousEnd,
            TimelineInput input) {
        if (value(day.getDay()) > 1 && previousEnd != null) {
            return previousEnd;
        }
        if (hasRental(input) && input.getRentalTripContext() != null) {
            RentalTripContextDTO rental = input.getRentalTripContext();
            TripPlanDTO.Anchor anchor = new TripPlanDTO.Anchor();
            anchor.setType(TYPE_RENTAL_PICKUP);
            anchor.setName(
                    firstNonBlank(
                            rental.getMatchedStore() == null
                                    ? null
                                    : rental.getMatchedStore().getDisplayName(),
                            rental.getArrivalPoint() == null
                                    ? null
                                    : rental.getArrivalPoint().getName()));
            anchor.setCity(
                    rental.getMatchedStore() == null
                            ? null
                            : rental.getMatchedStore().getCityName());
            anchor.setArea(
                    rental.getMatchedStore() == null
                            ? null
                            : rental.getMatchedStore().getDisplayName());
            anchor.setAddress(
                    rental.getArrivalPoint() == null ? null : rental.getArrivalPoint().getName());
            if (rental.getMatchedStore() != null) {
                anchor.setLng(decimal(rental.getMatchedStore().getLng()));
                anchor.setLat(decimal(rental.getMatchedStore().getLat()));
            }
            anchor.setCoordType("GCJ02");
            return anchor;
        }
        return spotAnchor(firstSpot, "DAY_START");
    }

    private TripPlanDTO.Anchor hotelAnchor(TripPlanDTO.DailyPlan day, TimelineInput input) {
        TripPlanDTO.Spot lastSpot = lastSpot(day);
        PoiCandidate hotel = hotelCandidate(day, input);
        if (hotel != null) {
            BigDecimal[] location = parseLocation(hotel.getLocation());
            if (location[0] != null
                    && location[1] != null
                    && isNearLastSpot(lastSpot, location, MAX_HOTEL_ANCHOR_DISTANCE_KM)) {
                return new TripPlanDTO.Anchor(
                        TYPE_STAY_AREA,
                        firstNonBlank(
                                hotel.getName(),
                                firstNonBlank(hotel.getArea(), day.getCity() + "住宿区域")),
                        firstNonBlank(hotel.getCity(), day.getCity()),
                        firstNonBlank(hotel.getArea(), hotel.getBusinessArea()),
                        hotel.getAddress(),
                        location[0],
                        location[1],
                        "GCJ02");
            }
        }
        TripPlanDTO.Anchor nearLastSpot = spotAnchor(lastSpot, TYPE_STAY_AREA);
        if (nearLastSpot != null) {
            nearLastSpot.setName(
                    firstNonBlank(nearLastSpot.getArea(), nearLastSpot.getName()) + "住宿区域");
            return nearLastSpot;
        }
        DaySkeleton skeleton = skeleton(day, input);
        TripPlanDTO.Anchor plannedStay =
                snapshotAnchor(skeleton == null ? null : skeleton.getStayArea(), TYPE_STAY_AREA);
        if (plannedStay != null) {
            return plannedStay;
        }
        TripPlanDTO.Anchor anchor = spotAnchor(lastSpot, TYPE_STAY_AREA);
        if (anchor == null) {
            anchor = new TripPlanDTO.Anchor();
            anchor.setType(TYPE_STAY_AREA);
            anchor.setName(
                    firstNonBlank(day.getDiningArea(), firstNonBlank(day.getCity(), "住宿区域")));
            anchor.setCity(day.getCity());
            anchor.setArea(day.getDiningArea());
        } else {
            anchor.setType(TYPE_STAY_AREA);
            anchor.setName(firstNonBlank(anchor.getArea(), anchor.getName()) + "住宿区域");
        }
        return anchor;
    }

    private boolean isNearLastSpot(
            TripPlanDTO.Spot lastSpot, BigDecimal[] location, double maxDistanceKm) {
        double[] spotLocation = spotLocation(lastSpot);
        if (spotLocation == null
                || location == null
                || location[0] == null
                || location[1] == null) {
            return false;
        }
        return distanceKm(
                        spotLocation,
                        new double[] {location[0].doubleValue(), location[1].doubleValue()})
                <= maxDistanceKm;
    }

    private TripPlanDTO.Anchor snapshotAnchor(AreaAnchorSnapshot snapshot, String type) {
        if (snapshot == null) {
            return null;
        }
        BigDecimal[] location = parseLocation(snapshot.getLocation());
        if (location[0] == null || location[1] == null) {
            return null;
        }
        return new TripPlanDTO.Anchor(
                type,
                firstNonBlank(snapshot.getName(), snapshot.getArea()),
                snapshot.getCity(),
                snapshot.getArea(),
                snapshot.getAddress(),
                location[0],
                location[1],
                "GCJ02");
    }

    private DaySkeleton skeleton(TripPlanDTO.DailyPlan day, TimelineInput input) {
        if (day == null || input == null || input.getDaySkeletons() == null) {
            return null;
        }
        return input.getDaySkeletons().stream()
                .filter(item -> value(item.getDay()).equals(value(day.getDay())))
                .findFirst()
                .orElse(null);
    }

    private TripPlanDTO.Anchor endAnchor(TripPlanDTO.DailyPlan day) {
        if (day.getEndAnchor() != null) {
            return day.getEndAnchor();
        }
        return hotelAnchor(day, TimelineInput.empty());
    }

    private TripPlanDTO.Anchor spotAnchor(TripPlanDTO.Spot spot, String type) {
        if (spot == null) {
            return null;
        }
        return new TripPlanDTO.Anchor(
                type,
                spot.getName(),
                spot.getCity(),
                spot.getArea(),
                spot.getAddress(),
                firstNonNull(spot.getEntranceLng(), spot.getLng()),
                firstNonNull(spot.getEntranceLat(), spot.getLat()),
                firstNonBlank(spot.getCoordType(), "GCJ02"));
    }

    private void applyAnchor(TripPlanDTO.TimelineNode node, TripPlanDTO.Anchor anchor) {
        if (anchor == null) {
            return;
        }
        node.setCity(anchor.getCity());
        node.setArea(anchor.getArea());
        node.setAddress(anchor.getAddress());
        node.setLng(anchor.getLng());
        node.setLat(anchor.getLat());
        node.setCoordType(anchor.getCoordType());
    }

    private String routeSuggestion(
            TripPlanDTO.DailyPlan day, TripPlanDTO.Spot previous, TripPlanDTO.Spot current) {
        if (current == null || day.getRouteLegs() == null) {
            return null;
        }
        Integer fromOrder = previous == null ? null : previous.getOrder();
        Integer toOrder = current.getOrder();
        return day.getRouteLegs().stream()
                .filter(leg -> toOrder != null && toOrder.equals(leg.getToOrder()))
                .filter(leg -> fromOrder == null || fromOrder.equals(leg.getFromOrder()))
                .map(TripPlanDTO.RouteLeg::getSuggestion)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private String startRouteSuggestion(
            TripPlanDTO.Anchor previousEnd,
            TripPlanDTO.DailyPlan day,
            TripPlanDTO.Spot firstSpot,
            TimelineInput input) {
        if (firstSpot == null) {
            return null;
        }
        if (previousEnd == null) {
            return routeSuggestion(day, null, firstSpot);
        }
        int minutes = transferMinutes(previousEnd, firstSpot, day, input);
        return "从住宿区域出发，"
                + (hasRental(input) ? "自驾" : "交通")
                + "约"
                + readableMinutes(minutes)
                + "抵达。";
    }

    private List<TripPlanDTO.Spot> orderedSpots(TripPlanDTO.DailyPlan day) {
        if (day == null || day.getSpots() == null) {
            return List.of();
        }
        return day.getSpots().stream()
                .sorted(Comparator.comparing(spot -> value(spot.getOrder())))
                .toList();
    }

    private TripPlanDTO.Spot lastSpot(TripPlanDTO.DailyPlan day) {
        List<TripPlanDTO.Spot> spots = orderedSpots(day);
        return spots.isEmpty() ? null : spots.get(spots.size() - 1);
    }

    private TripPlanDTO.FoodSuggestion foodSuggestion(
            TripPlanDTO.DailyPlan day, String type, TripPlanDTO.Spot routeReference) {
        if (day == null || day.getFoodSuggestions() == null) {
            return null;
        }
        String meal = TYPE_LUNCH_AREA.equals(type) ? "LUNCH" : "DINNER";
        List<TripPlanDTO.FoodSuggestion> matched =
                day.getFoodSuggestions().stream()
                        .filter(item -> meal.equalsIgnoreCase(item.getMeal()))
                        .toList();
        List<TripPlanDTO.FoodSuggestion> candidates =
                matched.isEmpty() ? day.getFoodSuggestions() : matched;
        if (routeReference != null && spotLocation(routeReference) != null) {
            double[] reference = spotLocation(routeReference);
            return candidates.stream()
                    .filter(item -> foodLocation(item) != null)
                    .min(
                            Comparator.comparingDouble(
                                    item -> distanceKm(reference, foodLocation(item))))
                    .orElse(candidates.isEmpty() ? null : candidates.get(0));
        }
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private TripPlanDTO.Spot referenceSpot(
            List<TripPlanDTO.Spot> spots, int previousIndex, int nextIndex) {
        if (spots == null || spots.isEmpty()) {
            return null;
        }
        if (previousIndex >= 0 && previousIndex < spots.size()) {
            return spots.get(previousIndex);
        }
        if (nextIndex >= 0 && nextIndex < spots.size()) {
            return spots.get(nextIndex);
        }
        return spots.get(spots.size() - 1);
    }

    private TripPlanDTO.Spot lastTimelineSpot(
            List<TripPlanDTO.TimelineNode> timeline, List<TripPlanDTO.Spot> spots) {
        if (timeline == null || spots == null || spots.isEmpty()) {
            return spots == null || spots.isEmpty() ? null : spots.get(spots.size() - 1);
        }
        for (int index = timeline.size() - 1; index >= 0; index--) {
            TripPlanDTO.TimelineNode node = timeline.get(index);
            if (node.getToOrder() == null) {
                continue;
            }
            Integer order = node.getToOrder();
            return spots.stream()
                    .filter(spot -> order.equals(spot.getOrder()))
                    .findFirst()
                    .orElse(spots.get(spots.size() - 1));
        }
        return spots.get(spots.size() - 1);
    }

    private boolean canFitSpotBefore(
            int startMinutes, TripPlanDTO.Spot spot, int latestEndMinutes, TimelineInput input) {
        int projectedEnd = startMinutes + duration(spot) + routeBuffer(input);
        return projectedEnd <= latestEndMinutes;
    }

    private int morningSpotDuration(int startMinutes, TripPlanDTO.Spot spot) {
        if (spot == null || startMinutes >= LUNCH_EARLIEST) {
            return 0;
        }
        int available = LUNCH_EARLIEST - startMinutes - 10;
        if (available < 45) {
            return 0;
        }
        return Math.min(duration(spot), Math.min(available, 90));
    }

    private double[] spotLocation(TripPlanDTO.Spot spot) {
        if (spot == null) {
            return null;
        }
        BigDecimal lng = firstNonNull(spot.getEntranceLng(), spot.getLng());
        BigDecimal lat = firstNonNull(spot.getEntranceLat(), spot.getLat());
        if (lng == null || lat == null) {
            return null;
        }
        return new double[] {lng.doubleValue(), lat.doubleValue()};
    }

    private double[] foodLocation(TripPlanDTO.FoodSuggestion food) {
        if (food == null || food.getLng() == null || food.getLat() == null) {
            return null;
        }
        return new double[] {food.getLng().doubleValue(), food.getLat().doubleValue()};
    }

    private double distanceKm(double[] from, double[] to) {
        if (from == null || to == null) {
            return Double.MAX_VALUE;
        }
        return com.sora.aitravel.service.route.GeoRouteCalculator.distanceKm(
                from[0], from[1], to[0], to[1]);
    }

    private PoiCandidate hotelCandidate(TripPlanDTO.DailyPlan day, TimelineInput input) {
        if (input == null || input.getRankedDayDataPackages() == null) {
            return null;
        }
        List<PoiCandidate> hotels =
                input.getRankedDayDataPackages().stream()
                        .filter(item -> value(item.getDay()).equals(value(day.getDay())))
                        .findFirst()
                        .map(DayDataPackage::hotelCandidates)
                        .filter(list -> list != null && !list.isEmpty())
                        .orElse(List.of());
        if (hotels.isEmpty()) {
            return null;
        }
        double[] reference = spotLocation(lastSpot(day));
        if (reference == null) {
            return hotels.get(0);
        }
        return hotels.stream()
                .filter(item -> parseLocation(item.getLocation())[0] != null)
                .min(
                        Comparator.comparingDouble(
                                item -> {
                                    BigDecimal[] location = parseLocation(item.getLocation());
                                    double[] hotelLocation =
                                            new double[] {
                                                location[0].doubleValue(), location[1].doubleValue()
                                            };
                                    return distanceKm(reference, hotelLocation);
                                }))
                .orElse(hotels.get(0));
    }

    private boolean isNightSpot(TripPlanDTO.Spot spot) {
        String text =
                (firstNonBlank(spot.getType(), "") + " " + firstNonBlank(spot.getName(), ""))
                        .toUpperCase();
        return text.contains("NIGHT")
                || text.contains("夜景")
                || text.contains("夜市")
                || text.contains("夜游");
    }

    private String dayStartTime(TripPlanDTO.DailyPlan day, TimelineInput input) {
        if (value(day.getDay()) > 1) {
            return "09:00";
        }
        if (hasRental(input)) {
            return pickupStartTime(input);
        }
        return "09:30";
    }

    private String pickupStartTime(TimelineInput input) {
        String range =
                input == null || input.getRentalTripContext() == null
                        ? ""
                        : firstNonBlank(input.getRentalTripContext().getArrivalTimeRange(), "");
        if (range.matches(".*\\d{1,2}:\\d{2}.*")) {
            return range.replaceAll("^.*?(\\d{1,2}:\\d{2}).*$", "$1");
        }
        if (range.contains("中午")) {
            return "12:30";
        }
        if (range.contains("下午")) {
            return "14:30";
        }
        if (range.contains("晚上")) {
            return "18:30";
        }
        return "09:30";
    }

    private String lunchTime(int cursor) {
        return mealTime(cursor, LUNCH_EARLIEST, LUNCH_LATEST);
    }

    private String dinnerTime(int cursor) {
        return mealTime(cursor, DINNER_EARLIEST, DINNER_LATEST);
    }

    private String hotelTime(int cursor) {
        return mealTime(cursor, HOTEL_EARLIEST, HOTEL_LATEST);
    }

    private String mealTime(int cursor, int earliest, int latest) {
        return formatTime(Math.min(Math.max(cursor, earliest), latest));
    }

    private int transferMinutes(
            TripPlanDTO.Anchor previousEnd,
            TripPlanDTO.Spot firstSpot,
            TripPlanDTO.DailyPlan day,
            TimelineInput input) {
        Integer coordinateMinutes = coordinateTransferMinutes(previousEnd, firstSpot, input);
        if (coordinateMinutes != null) {
            return coordinateMinutes;
        }
        if (crossCity(previousEnd, day)) {
            return hasRental(input) ? 120 : 150;
        }
        return hasRental(input) ? 35 : 45;
    }

    private Integer coordinateTransferMinutes(
            TripPlanDTO.Anchor previousEnd, TripPlanDTO.Spot firstSpot, TimelineInput input) {
        if (previousEnd == null
                || firstSpot == null
                || previousEnd.getLng() == null
                || previousEnd.getLat() == null) {
            return null;
        }
        double[] spot = spotLocation(firstSpot);
        if (spot == null) {
            return null;
        }
        int meters =
                com.sora.aitravel.service.route.GeoRouteCalculator.roadDistanceMeters(
                        previousEnd.getLng().doubleValue(),
                        previousEnd.getLat().doubleValue(),
                        spot[0],
                        spot[1]);
        int seconds =
                hasRental(input)
                        ? com.sora.aitravel.service.route.GeoRouteCalculator.drivingSeconds(meters)
                        : Math.max(900, meters / 450 * 60);
        return Math.max(15, (int) Math.ceil(seconds / 60.0));
    }

    private boolean crossCity(TripPlanDTO.Anchor previousEnd, TripPlanDTO.DailyPlan day) {
        String previousCity = normalizeCity(previousEnd == null ? null : previousEnd.getCity());
        String currentCity = normalizeCity(day == null ? null : day.getCity());
        return !previousCity.isBlank()
                && !currentCity.isBlank()
                && !previousCity.equals(currentCity);
    }

    private int routeBuffer(TimelineInput input) {
        return hasRental(input) ? 35 : 25;
    }

    private boolean hasRental(TimelineInput input) {
        return input != null && input.getSelectedQuote() != null;
    }

    private boolean isLastDay(TripPlanDTO.DailyPlan day, TimelineInput input) {
        TravelRequirementDTO requirement = input == null ? null : input.getRequirement();
        return requirement != null && value(day.getDay()).equals(value(requirement.getDays()));
    }

    private int duration(TripPlanDTO.Spot spot) {
        return Math.max(45, value(spot == null ? null : spot.getSuggestedDurationMinutes(), 120));
    }

    private BigDecimal[] parseLocation(String location) {
        if (location == null || location.isBlank() || !location.contains(",")) {
            return new BigDecimal[] {null, null};
        }
        try {
            String[] parts = location.split(",");
            return new BigDecimal[] {
                new BigDecimal(parts[0].trim()), new BigDecimal(parts[1].trim())
            };
        } catch (RuntimeException exception) {
            return new BigDecimal[] {null, null};
        }
    }

    private BigDecimal decimal(String value) {
        try {
            return value == null || value.isBlank() ? null : new BigDecimal(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String readableMinutes(int minutes) {
        if (minutes < 60) {
            return minutes + "分钟";
        }
        int hours = minutes / 60;
        int rest = minutes % 60;
        return rest == 0 ? hours + "小时" : hours + "小时" + rest + "分钟";
    }

    private Integer addMinutes(String time, int minutes) {
        return parseTime(time) + minutes;
    }

    private int parseTime(String time) {
        if (time == null || !time.matches("\\d{1,2}:\\d{2}")) {
            return 9 * 60 + 30;
        }
        String[] parts = time.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }

    private String formatTime(int minutes) {
        int safe = Math.max(0, Math.min(minutes, 23 * 60 + 59));
        return "%02d:%02d".formatted(safe / 60, safe % 60);
    }

    private Integer value(Integer value) {
        return value == null ? 0 : value;
    }

    private Integer value(Integer value, Integer fallback) {
        return value == null ? fallback : value;
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private BigDecimal firstNonNull(BigDecimal first, BigDecimal second) {
        return first != null ? first : second;
    }

    private TripPlanDTO.DailyPlan findDay(List<TripPlanDTO.DailyPlan> days, int dayNo) {
        return days == null
                ? null
                : days.stream()
                        .filter(day -> value(day.getDay()) == dayNo)
                        .findFirst()
                        .orElse(null);
    }

    private String normalizeCity(String value) {
        return value == null ? "" : value.replace("市", "").replaceAll("\\s+", "").trim();
    }

    public static final class TimelineInput {
        private final List<TripPlanDTO.DailyPlan> previousDays;
        private final List<TripPlanDTO.DailyPlan> currentDays;
        private final TravelRequirementDTO requirement;
        private final RentalQuoteOptionDTO selectedQuote;
        private final RentalTripContextDTO rentalTripContext;
        private final List<DaySkeleton> daySkeletons;
        private final List<DayDataPackage> rankedDayDataPackages;

        public TimelineInput(
                List<TripPlanDTO.DailyPlan> previousDays,
                List<TripPlanDTO.DailyPlan> currentDays,
                TravelRequirementDTO requirement,
                RentalQuoteOptionDTO selectedQuote,
                RentalTripContextDTO rentalTripContext,
                List<DaySkeleton> daySkeletons,
                List<DayDataPackage> rankedDayDataPackages) {
            this.previousDays = previousDays == null ? List.of() : previousDays;
            this.currentDays = currentDays == null ? List.of() : currentDays;
            this.requirement = requirement;
            this.selectedQuote = selectedQuote;
            this.rentalTripContext = rentalTripContext;
            this.daySkeletons = daySkeletons == null ? List.of() : daySkeletons;
            this.rankedDayDataPackages =
                    rankedDayDataPackages == null ? List.of() : rankedDayDataPackages;
        }

        private static TimelineInput empty() {
            return new TimelineInput(List.of(), List.of(), null, null, null, List.of(), List.of());
        }

        private List<TripPlanDTO.DailyPlan> getPreviousDays() {
            return previousDays;
        }

        private List<TripPlanDTO.DailyPlan> getCurrentDays() {
            return currentDays;
        }

        private TravelRequirementDTO getRequirement() {
            return requirement;
        }

        private RentalQuoteOptionDTO getSelectedQuote() {
            return selectedQuote;
        }

        private RentalTripContextDTO getRentalTripContext() {
            return rentalTripContext;
        }

        private List<DaySkeleton> getDaySkeletons() {
            return daySkeletons;
        }

        private List<DayDataPackage> getRankedDayDataPackages() {
            return rankedDayDataPackages;
        }
    }

    private static final class TimelineClock {
        private int minutes;

        private TimelineClock(String startTime) {
            this.minutes = parse(startTime);
        }

        private String time() {
            int safe = Math.max(0, Math.min(minutes, 23 * 60 + 59));
            return "%02d:%02d".formatted(safe / 60, safe % 60);
        }

        private int minutes() {
            return minutes;
        }

        private void move(int value) {
            minutes += value;
        }

        private void reset(int value) {
            minutes = value;
        }

        private static int parse(String time) {
            if (time == null || !time.matches("\\d{1,2}:\\d{2}")) {
                return 9 * 60 + 30;
            }
            String[] parts = time.split(":");
            return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        }
    }
}
