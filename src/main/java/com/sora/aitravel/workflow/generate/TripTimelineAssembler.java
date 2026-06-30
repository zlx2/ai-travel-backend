package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.dto.model.RentalTripContextDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.model.TripPlanDTO;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/** Builds the frontend timeline from backend-owned itinerary facts. */
@Component
public class TripTimelineAssembler {

    private static final String TYPE_DAY_START = "DAY_START";
    private static final String TYPE_TRANSFER = "TRANSFER";
    private static final String TYPE_RENTAL_PICKUP = "RENTAL_PICKUP";
    private static final String TYPE_RENTAL_RETURN = "RENTAL_RETURN";
    private static final String TYPE_LUNCH = "LUNCH";
    private static final String TYPE_DINNER = "DINNER";
    private static final String TYPE_HOTEL = "HOTEL";
    private static final String TYPE_HOTEL_AREA = "HOTEL_AREA";
    private static final String TYPE_SCENIC = "SCENIC";

    public void execute(GenerateWorkflowContext context) {
        assemble(List.of(), context.getLockedDailyPlans(), context);
    }

    public void assemble(
            List<TripPlanDTO.DailyPlan> previousDays,
            List<TripPlanDTO.DailyPlan> currentDays,
            GenerateWorkflowContext context) {
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
            assembleDay(day, previousDay, context);
        }
    }

    private void assembleDay(
            TripPlanDTO.DailyPlan day,
            TripPlanDTO.DailyPlan previousDay,
            GenerateWorkflowContext context) {
        List<TripPlanDTO.Spot> spots = orderedSpots(day);
        List<TripPlanDTO.Spot> daytimeSpots = spots.stream().filter(spot -> !isNightSpot(spot)).toList();
        List<TripPlanDTO.Spot> nightSpots = spots.stream().filter(this::isNightSpot).toList();
        TripPlanDTO.Spot firstSpot = spots.isEmpty() ? null : spots.get(0);
        TripPlanDTO.Anchor previousEnd = previousDay == null ? null : endAnchor(previousDay);
        TripPlanDTO.Anchor hotelAnchor = hotelAnchor(day, context);

        day.setStartAnchor(startAnchor(day, firstSpot, previousEnd, context));
        day.setEndAnchor(hotelAnchor);

        List<TripPlanDTO.TimelineNode> timeline = new ArrayList<>();
        TimelineClock clock = new TimelineClock(dayStartTime(day, context));
        int order = 1;

        if (value(day.getDay()) > 1 && previousEnd != null) {
            timeline.add(dayStartNode(order++, clock.time(), previousEnd, firstSpot));
            clock.move(20);
            timeline.add(transferNode(order++, clock.time(), previousEnd, firstSpot, day, context));
            clock.move(transferMinutes(previousEnd, firstSpot, day, context) + 15);
        }

        if (value(day.getDay()) == 1 && hasRental(context)) {
            TripPlanDTO.TimelineNode pickup = pickupNode(order++, pickupStartTime(context), context);
            timeline.add(pickup);
            clock.reset(Math.max(clock.minutes(), addMinutes(pickup.getStartTime(), 45)));
        }

        if (!daytimeSpots.isEmpty()) {
            timeline.add(spotNode(order++, clock.time(), daytimeSpots.get(0), routeSuggestion(day, null, daytimeSpots.get(0))));
            clock.move(duration(daytimeSpots.get(0)) + routeBuffer(context));
        }

        timeline.add(mealNode(order++, lunchTime(clock.minutes()), TYPE_LUNCH, day, context));
        clock.reset(Math.max(addMinutes(timeline.get(timeline.size() - 1).getStartTime(), 65), 13 * 60 + 20));

        for (int index = 1; index < daytimeSpots.size(); index++) {
            TripPlanDTO.Spot previous = daytimeSpots.get(index - 1);
            TripPlanDTO.Spot spot = daytimeSpots.get(index);
            timeline.add(spotNode(order++, clock.time(), spot, routeSuggestion(day, previous, spot)));
            clock.move(duration(spot) + routeBuffer(context));
        }

        timeline.add(mealNode(order++, dinnerTime(clock.minutes()), TYPE_DINNER, day, context));
        clock.reset(Math.max(addMinutes(timeline.get(timeline.size() - 1).getStartTime(), 80), 19 * 60 + 20));

        for (TripPlanDTO.Spot nightSpot : nightSpots) {
            timeline.add(spotNode(order++, clock.time(), nightSpot, routeSuggestion(day, null, nightSpot)));
            clock.move(duration(nightSpot) + routeBuffer(context));
        }

        TripPlanDTO.TimelineNode hotel = hotelNode(order++, hotelTime(clock.minutes()), day, hotelAnchor);
        timeline.add(hotel);

        if (isLastDay(day, context) && hasRental(context)) {
            int returnTime = Math.max(addMinutes(hotel.getStartTime(), 35), 20 * 60 + 30);
            timeline.add(returnNode(order++, formatTime(returnTime), context));
        }

        day.setTimeline(timeline);
    }

    private TripPlanDTO.TimelineNode dayStartNode(
            int order, String time, TripPlanDTO.Anchor previousEnd, TripPlanDTO.Spot firstSpot) {
        TripPlanDTO.TimelineNode node = compactNode(order, TYPE_DAY_START, time, "从酒店出发");
        node.setSubtitle(firstSpot == null ? "开始今天的行程" : "前往" + firstSpot.getName());
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
            GenerateWorkflowContext context) {
        String to = firstSpot == null ? firstNonBlank(day.getCity(), "今日首站") : firstSpot.getName();
        TripPlanDTO.TimelineNode node = compactNode(order, TYPE_TRANSFER, time, "前往" + to);
        int minutes = transferMinutes(previousEnd, firstSpot, day, context);
        node.setSubtitle((hasRental(context) ? "自驾" : "交通") + "约" + readableMinutes(minutes));
        node.setDescription(node.getSubtitle());
        applyAnchor(node, previousEnd);
        node.setDurationMinutes(minutes);
        node.setDurationText("约" + readableMinutes(minutes));
        node.setTransportSuggestion(hasRental(context) ? "建议按实时路况出发。" : "建议按当天位置选择打车或公共交通。");
        node.setSource("DAY_ANCHOR");
        node.setFromAnchor(previousEnd.getName());
        node.setToAnchor(to);
        node.setTags(List.of(crossCity(previousEnd, day) ? "跨城" : "路程", hasRental(context) ? "自驾" : "交通"));
        return withEndTime(node);
    }

    private TripPlanDTO.TimelineNode pickupNode(int order, String time, GenerateWorkflowContext context) {
        RentalTripContextDTO rental = context.getRentalTripContext();
        TripPlanDTO.TimelineNode node = compactNode(order, TYPE_RENTAL_PICKUP, time, "取车");
        String point = rental == null || rental.getArrivalPoint() == null ? null : rental.getArrivalPoint().getName();
        String store = rental == null || rental.getMatchedStore() == null ? null : rental.getMatchedStore().getDisplayName();
        node.setSubtitle(firstNonBlank(store, firstNonBlank(point, "到达点附近取车")));
        node.setDescription("完成验车后开始行程。");
        node.setArea(store);
        node.setAddress(point);
        if (rental != null && rental.getMatchedStore() != null) {
            node.setLng(decimal(rental.getMatchedStore().getLng()));
            node.setLat(decimal(rental.getMatchedStore().getLat()));
            node.setCity(rental.getMatchedStore().getCityName());
        } else if (context.getSelectedQuote() != null) {
            node.setLng(context.getSelectedQuote().getPickupLng());
            node.setLat(context.getSelectedQuote().getPickupLat());
            node.setArea(firstNonBlank(context.getSelectedQuote().getPickupPoiName(), node.getArea()));
            node.setAddress(firstNonBlank(context.getSelectedQuote().getPickupAddress(), node.getAddress()));
        }
        node.setCoordType("GCJ02");
        node.setDurationMinutes(30);
        node.setDurationText("约30分钟");
        node.setSource("RENTAL_CONTEXT");
        node.setTags(List.of("取车", "租车"));
        return withEndTime(node);
    }

    private TripPlanDTO.TimelineNode returnNode(int order, String time, GenerateWorkflowContext context) {
        TripPlanDTO.TimelineNode node = compactNode(order, TYPE_RENTAL_RETURN, time, "还车");
        RentalTripContextDTO rental = context.getRentalTripContext();
        String point = rental == null ? null : firstNonBlank(rental.getReturnPoint(), rental.getArrivalPoint() == null ? null : rental.getArrivalPoint().getName());
        node.setSubtitle(firstNonBlank(point, "行程结束点附近还车"));
        node.setDescription("预留验车和交接时间。");
        if (rental != null && rental.getMatchedStore() != null) {
            node.setArea(rental.getMatchedStore().getDisplayName());
            node.setLng(decimal(rental.getMatchedStore().getLng()));
            node.setLat(decimal(rental.getMatchedStore().getLat()));
            node.setCity(rental.getMatchedStore().getCityName());
        } else if (context.getSelectedQuote() != null) {
            node.setArea(context.getSelectedQuote().getReturnPoiName());
            node.setLng(context.getSelectedQuote().getReturnLng());
            node.setLat(context.getSelectedQuote().getReturnLat());
        }
        node.setAddress(firstNonBlank(point, context.getSelectedQuote() == null ? null : context.getSelectedQuote().getReturnAddress()));
        node.setCoordType("GCJ02");
        node.setDurationMinutes(30);
        node.setDurationText("约30分钟");
        node.setSource("RENTAL_CONTEXT");
        node.setTags(List.of("还车", "租车"));
        return withEndTime(node);
    }

    private TripPlanDTO.TimelineNode mealNode(
            int order, String time, String type, TripPlanDTO.DailyPlan day, GenerateWorkflowContext context) {
        TripPlanDTO.FoodSuggestion food = foodSuggestion(day, type);
        String title = TYPE_LUNCH.equals(type) ? "午餐" : "晚餐";
        TripPlanDTO.TimelineNode node = compactNode(order, type, time, title);
        String area = firstNonBlank(food == null ? null : food.getArea(), firstNonBlank(day.getDiningArea(), "就近用餐"));
        String mealStyle = food == null ? "本地餐馆" : firstNonBlank(food.getName(), "本地餐馆");
        node.setSubtitle(area + "附近 · " + mealStyle);
        node.setDescription(node.getSubtitle());
        node.setArea(area);
        if (food != null) {
            node.setCity(food.getCity());
            node.setAddress(food.getAddress());
            node.setLng(food.getLng());
            node.setLat(food.getLat());
            node.setCoordType(firstNonBlank(food.getCoordType(), "GCJ02"));
        }
        node.setDurationMinutes(TYPE_LUNCH.equals(type) ? 60 : 75);
        node.setDurationText(TYPE_LUNCH.equals(type) ? "约1小时" : "约1小时15分钟");
        int people = context.getRequirement() == null || context.getRequirement().getPeopleCount() == null
                ? 1
                : context.getRequirement().getPeopleCount();
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
        TripPlanDTO.TimelineNode node = compactNode(order, TYPE_HOTEL, time, "入住酒店");
        node.setSubtitle("建议住在" + firstNonBlank(hotelAnchor.getArea(), hotelAnchor.getName()));
        node.setDescription("回酒店休息。");
        applyAnchor(node, hotelAnchor);
        node.setDurationMinutes(30);
        node.setDurationText("约30分钟");
        node.setSource("HOTEL_AREA");
        node.setTags(List.of("住宿", "休息"));
        return withEndTime(node);
    }

    private TripPlanDTO.TimelineNode spotNode(
            int order, String time, TripPlanDTO.Spot spot, String transportSuggestion) {
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
        node.setDurationMinutes(duration(spot));
        node.setDurationText(firstNonBlank(spot.getSuggestedDurationText(), "约" + readableMinutes(duration(spot))));
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

    private TripPlanDTO.TimelineNode compactNode(int order, String type, String time, String title) {
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
            GenerateWorkflowContext context) {
        if (value(day.getDay()) > 1 && previousEnd != null) {
            return previousEnd;
        }
        if (hasRental(context) && context.getRentalTripContext() != null) {
            RentalTripContextDTO rental = context.getRentalTripContext();
            TripPlanDTO.Anchor anchor = new TripPlanDTO.Anchor();
            anchor.setType(TYPE_RENTAL_PICKUP);
            anchor.setName(firstNonBlank(
                    rental.getMatchedStore() == null ? null : rental.getMatchedStore().getDisplayName(),
                    rental.getArrivalPoint() == null ? null : rental.getArrivalPoint().getName()));
            anchor.setCity(rental.getMatchedStore() == null ? null : rental.getMatchedStore().getCityName());
            anchor.setArea(rental.getMatchedStore() == null ? null : rental.getMatchedStore().getDisplayName());
            anchor.setAddress(rental.getArrivalPoint() == null ? null : rental.getArrivalPoint().getName());
            if (rental.getMatchedStore() != null) {
                anchor.setLng(decimal(rental.getMatchedStore().getLng()));
                anchor.setLat(decimal(rental.getMatchedStore().getLat()));
            }
            anchor.setCoordType("GCJ02");
            return anchor;
        }
        return spotAnchor(firstSpot, "DAY_START");
    }

    private TripPlanDTO.Anchor hotelAnchor(TripPlanDTO.DailyPlan day, GenerateWorkflowContext context) {
        PoiCandidate hotel = hotelCandidate(day, context);
        if (hotel != null) {
            BigDecimal[] location = parseLocation(hotel.getLocation());
            return new TripPlanDTO.Anchor(
                    TYPE_HOTEL_AREA,
                    firstNonBlank(hotel.getName(), firstNonBlank(hotel.getArea(), day.getCity() + "住宿区域")),
                    firstNonBlank(hotel.getCity(), day.getCity()),
                    firstNonBlank(hotel.getArea(), hotel.getBusinessArea()),
                    hotel.getAddress(),
                    location[0],
                    location[1],
                    "GCJ02");
        }
        TripPlanDTO.Spot lastSpot = lastSpot(day);
        TripPlanDTO.Anchor anchor = spotAnchor(lastSpot, TYPE_HOTEL_AREA);
        if (anchor == null) {
            anchor = new TripPlanDTO.Anchor();
            anchor.setType(TYPE_HOTEL_AREA);
            anchor.setName(firstNonBlank(day.getDiningArea(), firstNonBlank(day.getCity(), "住宿区域")));
            anchor.setCity(day.getCity());
            anchor.setArea(day.getDiningArea());
        } else {
            anchor.setType(TYPE_HOTEL_AREA);
            anchor.setName(firstNonBlank(anchor.getArea(), anchor.getName()) + "住宿区域");
        }
        return anchor;
    }

    private TripPlanDTO.Anchor endAnchor(TripPlanDTO.DailyPlan day) {
        if (day.getEndAnchor() != null) {
            return day.getEndAnchor();
        }
        return hotelAnchor(day, null);
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

    private String routeSuggestion(TripPlanDTO.DailyPlan day, TripPlanDTO.Spot previous, TripPlanDTO.Spot current) {
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

    private TripPlanDTO.FoodSuggestion foodSuggestion(TripPlanDTO.DailyPlan day, String type) {
        if (day == null || day.getFoodSuggestions() == null) {
            return null;
        }
        String meal = TYPE_LUNCH.equals(type) ? "LUNCH" : "DINNER";
        return day.getFoodSuggestions().stream()
                .filter(item -> meal.equalsIgnoreCase(item.getMeal()))
                .findFirst()
                .orElse(day.getFoodSuggestions().isEmpty() ? null : day.getFoodSuggestions().get(0));
    }

    private PoiCandidate hotelCandidate(TripPlanDTO.DailyPlan day, GenerateWorkflowContext context) {
        if (context == null || context.getRankedDayDataPackages() == null) {
            return null;
        }
        return context.getRankedDayDataPackages().stream()
                .filter(item -> value(item.getDay()).equals(value(day.getDay())))
                .findFirst()
                .map(DayDataPackage::hotelCandidates)
                .filter(list -> list != null && !list.isEmpty())
                .map(list -> list.get(0))
                .orElse(null);
    }

    private boolean isNightSpot(TripPlanDTO.Spot spot) {
        String text = (firstNonBlank(spot.getType(), "") + " " + firstNonBlank(spot.getName(), "")).toUpperCase();
        return text.contains("NIGHT") || text.contains("夜景") || text.contains("夜市") || text.contains("夜游");
    }

    private String dayStartTime(TripPlanDTO.DailyPlan day, GenerateWorkflowContext context) {
        if (value(day.getDay()) > 1) {
            return "09:00";
        }
        if (hasRental(context)) {
            return pickupStartTime(context);
        }
        return "09:30";
    }

    private String pickupStartTime(GenerateWorkflowContext context) {
        String range =
                context == null || context.getRentalTripContext() == null
                        ? ""
                        : firstNonBlank(context.getRentalTripContext().getArrivalTimeRange(), "");
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
        return formatTime(Math.min(Math.max(cursor, 11 * 60 + 40), 12 * 60 + 40));
    }

    private String dinnerTime(int cursor) {
        return formatTime(Math.min(Math.max(cursor, 17 * 60 + 40), 18 * 60 + 40));
    }

    private String hotelTime(int cursor) {
        return formatTime(Math.min(Math.max(cursor, 20 * 60), 21 * 60));
    }

    private int transferMinutes(
            TripPlanDTO.Anchor previousEnd,
            TripPlanDTO.Spot firstSpot,
            TripPlanDTO.DailyPlan day,
            GenerateWorkflowContext context) {
        if (crossCity(previousEnd, day)) {
            return hasRental(context) ? 120 : 150;
        }
        return hasRental(context) ? 35 : 45;
    }

    private boolean crossCity(TripPlanDTO.Anchor previousEnd, TripPlanDTO.DailyPlan day) {
        String previousCity = normalizeCity(previousEnd == null ? null : previousEnd.getCity());
        String currentCity = normalizeCity(day == null ? null : day.getCity());
        return !previousCity.isBlank() && !currentCity.isBlank() && !previousCity.equals(currentCity);
    }

    private int routeBuffer(GenerateWorkflowContext context) {
        return hasRental(context) ? 35 : 25;
    }

    private boolean hasRental(GenerateWorkflowContext context) {
        return context != null && context.getSelectedQuote() != null;
    }

    private boolean isLastDay(TripPlanDTO.DailyPlan day, GenerateWorkflowContext context) {
        TravelRequirementDTO requirement = context == null ? null : context.getRequirement();
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
            return new BigDecimal[] {new BigDecimal(parts[0].trim()), new BigDecimal(parts[1].trim())};
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
        int safe = ((minutes % (24 * 60)) + (24 * 60)) % (24 * 60);
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
                : days.stream().filter(day -> value(day.getDay()) == dayNo).findFirst().orElse(null);
    }

    private String normalizeCity(String value) {
        return value == null ? "" : value.replace("市", "").replaceAll("\\s+", "").trim();
    }

    private static final class TimelineClock {
        private int minutes;

        private TimelineClock(String startTime) {
            this.minutes = parse(startTime);
        }

        private String time() {
            int safe = ((minutes % (24 * 60)) + (24 * 60)) % (24 * 60);
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
