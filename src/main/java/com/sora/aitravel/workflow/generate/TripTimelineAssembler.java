package com.sora.aitravel.workflow.generate;

import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.DAY_SKELETONS;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.LOCKED_DAILY_PLANS;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.PREVIOUS_DAILY_PLANS;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.RANKED_DAY_DATA_PACKAGES;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.RENTAL_TRIP_CONTEXT;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.REQUIREMENT;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.SELECTED_QUOTE;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.sora.aitravel.dto.model.RentalQuoteOptionDTO;
import com.sora.aitravel.dto.model.RentalTripContextDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.model.TripPlanDTO;
import com.sora.aitravel.model.AreaAnchorSnapshot;
import com.sora.aitravel.model.DayDataPackage;
import com.sora.aitravel.model.DaySkeleton;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 从后端拥有的行程事实构建前端时间线。 */
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
    private static final int DAYTIME_END_LIMIT = 18 * 60 + 45;
    private static final int NIGHT_END_LIMIT = 23 * 60;
    private static final int MIN_VISIT_MINUTES = 45;
    private static final int LUNCH_DURATION_MINUTES = 60;
    private static final int DINNER_DURATION_MINUTES = 75;
    private static final int MEAL_SETTLE_BUFFER_MINUTES = 15;
    private static final double NEXT_DAY_DIRECTION_THRESHOLD_KM = 45.0;
    private static final double STAY_DIRECTION_SHIFT_RATIO = 0.35;

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

    /**
     * 为单日组成时间线
     *
     * @param day
     * @param previousDay
     * @param input
     */
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
            if (firstSpot != null) {
                timeline.add(
                        transferNode(
                                order++,
                                clock.time(),
                                previousEnd,
                                firstSpot,
                                day,
                                previousDay,
                                input));
                clock.move(transferMinutes(previousEnd, firstSpot, day, input) + 10);
            } else {
                timeline.add(dayStartNode(order++, clock.time(), previousEnd, null, previousDay));
                clock.move(20);
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

        if (value(day.getDay()) == 1 && hasRental(input) && clock.minutes() >= DAYTIME_END_LIMIT) {
            daytimeSpots = List.of();
        }

        int nextDaytimeIndex = 0;
        while (nextDaytimeIndex < daytimeSpots.size() && clock.minutes() < LUNCH_EARLIEST) {
            TripPlanDTO.Spot current = daytimeSpots.get(nextDaytimeIndex);
            TripPlanDTO.Spot next = nextSpot(daytimeSpots, nextDaytimeIndex);
            int visitMinutes =
                    visitDurationBeforeWindow(
                            clock.minutes(), current, LUNCH_LATEST, lunchTransferBuffer(input));
            if (visitMinutes < MIN_VISIT_MINUTES) {
                break;
            }
            timeline.add(
                    spotNode(
                            order++,
                            clock.time(),
                            current,
                            startRouteSuggestion(previousEnd, day, current, input),
                            visitMinutes));
            clock.move(visitMinutes + transitionMinutes(day, current, next, input, true));
            nextDaytimeIndex++;
        }

        if (shouldAddLunch(clock.minutes(), nextDaytimeIndex)) {
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
                    addMinutes(
                                    timeline.get(timeline.size() - 1).getStartTime(),
                                    LUNCH_DURATION_MINUTES)
                            + MEAL_SETTLE_BUFFER_MINUTES);
        }

        for (int index = nextDaytimeIndex; index < daytimeSpots.size(); index++) {
            TripPlanDTO.Spot previous = index == 0 ? null : daytimeSpots.get(index - 1);
            TripPlanDTO.Spot spot = daytimeSpots.get(index);
            TripPlanDTO.Spot next = nextSpot(daytimeSpots, index);
            if (clock.minutes() >= DINNER_EARLIEST
                    || !canFitVisitBefore(
                            clock.minutes(),
                            spot,
                            DINNER_LATEST,
                            transitionMinutes(day, spot, next, input, true))) {
                break;
            }
            timeline.add(
                    spotNode(order++, clock.time(), spot, routeSuggestion(day, previous, spot)));
            clock.move(duration(spot) + transitionMinutes(day, spot, next, input, true));
        }

        if (shouldAddDinner(clock.minutes(), timeline, nightSpots)) {
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
                    addMinutes(
                                    timeline.get(timeline.size() - 1).getStartTime(),
                                    DINNER_DURATION_MINUTES)
                            + MEAL_SETTLE_BUFFER_MINUTES);
        }

        for (TripPlanDTO.Spot nightSpot : nightSpots) {
            int nightDuration = Math.min(duration(nightSpot), 110);
            if (!canFitVisitBefore(
                    clock.minutes(), nightDuration, NIGHT_END_LIMIT, nightTransferBuffer(input))) {
                break;
            }
            timeline.add(
                    spotNode(
                            order++,
                            clock.time(),
                            nightSpot,
                            routeSuggestion(day, null, nightSpot),
                            nightDuration));
            clock.move(nightDuration + nightTransferBuffer(input));
        }

        order = appendMissingSpotNodes(order, timeline, spots, clock, day, input);

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

        day.setTimeline(normalizeTimeline(timeline));
    }

    private int appendMissingSpotNodes(
            int order,
            List<TripPlanDTO.TimelineNode> timeline,
            List<TripPlanDTO.Spot> spots,
            TimelineClock clock,
            TripPlanDTO.DailyPlan day,
            TimelineInput input) {
        if (spots == null || spots.isEmpty()) {
            return order;
        }
        for (TripPlanDTO.Spot spot : spots) {
            if (spot == null || timelineContainsSpot(timeline, spot)) {
                continue;
            }
            int visitMinutes = Math.min(Math.max(MIN_VISIT_MINUTES, duration(spot)), 75);
            if (clock.minutes() + visitMinutes > NIGHT_END_LIMIT) {
                clock.reset(Math.max(DINNER_EARLIEST + DINNER_DURATION_MINUTES + MEAL_SETTLE_BUFFER_MINUTES, 19 * 60 + 10));
            }
            timeline.add(
                    spotNode(
                            order++,
                            clock.time(),
                            spot,
                            routeSuggestion(day, null, spot),
                            visitMinutes));
            clock.move(visitMinutes + nightTransferBuffer(input));
        }
        return order;
    }

    private boolean timelineContainsSpot(
            List<TripPlanDTO.TimelineNode> timeline, TripPlanDTO.Spot spot) {
        if (timeline == null || spot == null) {
            return false;
        }
        Integer order = spot.getOrder();
        String name = spot.getName();
        return timeline.stream()
                .anyMatch(
                        node ->
                                (order != null && order.equals(node.getToOrder()))
                                        || (name != null && name.equals(node.getTitle())));
    }

    /**
     * 生成每天时间线中的「出发」节点，表示从上一晚住宿地出发前往当天第一个景点。 该节点连接前一天的酒店与当天首站，同时携带酒店展示信息和价格标签，
     * 让前端能够完整展示"住→行→游"的行程衔接。
     *
     * @param order 节点在时间线中的序号
     * @param time 出发时间（如 "08:00"）
     * @param previousEnd 前一天的终点锚点（通常是酒店位置）
     * @param firstSpot 当天的第一个景点，可能为 null
     * @param previousDay 前一天的日程计划，用于获取酒店推荐数据
     * @return 带有结束时间的出发时间线节点
     */
    private TripPlanDTO.TimelineNode dayStartNode(
            int order,
            String time,
            TripPlanDTO.Anchor previousEnd,
            TripPlanDTO.Spot firstSpot,
            TripPlanDTO.DailyPlan previousDay) {
        // 创建 TYPE_DAY_START 类型的紧凑节点，标题为"从XX出发"（XX取前一天终点名称，默认"酒店"）
        TripPlanDTO.TimelineNode node =
                compactNode(
                        order,
                        TYPE_DAY_START,
                        time,
                        "从" + firstNonBlank(previousEnd.getName(), "酒店") + "出发");
        // 副标题：根据是否有今日首站，决定是否追加"前往今日第一站"
        node.setSubtitle(firstSpot == null ? "延续上一晚住宿区域" : "延续上一晚住宿区域，前往今日第一站");
        node.setDescription(node.getSubtitle());
        // 将前一天终点的位置坐标赋给该节点
        applyAnchor(node, previousEnd);
        // 默认出发耗时 20 分钟
        node.setDurationMinutes(20);
        node.setDurationText("约20分钟");
        // 标记来源为 DAY_ANCHOR，表示该节点由日程锚点生成
        node.setSource("DAY_ANCHOR");
        // 记录出发地和目的地名称
        node.setFromAnchor(previousEnd.getName());
        node.setToAnchor(firstSpot == null ? null : firstSpot.getName());
        // 将前一天的酒店数据挂到 DAY_START 节点上，供前端展示酒店地址和价格
        if (previousDay != null
                && previousDay.getNearbyHotels() != null
                && !previousDay.getNearbyHotels().isEmpty()) {
            node.setNearbyHotels(previousDay.getNearbyHotels());
        }
        // 构建标签列表：固定包含"出发"和"酒店"，若酒店有价格则追加预估价格
        List<String> startTags = new ArrayList<>();
        startTags.add("出发");
        startTags.add("酒店");
        if (previousDay != null
                && previousDay.getNearbyHotels() != null
                && !previousDay.getNearbyHotels().isEmpty()) {
            String price = previousDay.getNearbyHotels().get(0).getEstimatedPrice();
            if (price != null && !price.isBlank()) {
                startTags.add(price);
            }
        }
        node.setTags(startTags);
        // 计算并设置结束时间后返回
        return withEndTime(node);
    }

    private List<TripPlanDTO.TimelineNode> normalizeTimeline(
            List<TripPlanDTO.TimelineNode> timeline) {
        if (timeline == null || timeline.isEmpty()) {
            return timeline;
        }
        int cursor = 0;
        for (int index = 0; index < timeline.size(); index++) {
            TripPlanDTO.TimelineNode node = timeline.get(index);
            int start = parseTime(node.getStartTime());
            if (index > 0 && start < cursor) {
                node.setStartTime(formatTime(cursor));
            }
            node.setOrder(index + 1);
            withEndTime(node);
            cursor = addMinutes(node.getStartTime(), value(node.getDurationMinutes(), 0));
            cursor += transitionAfter(node);
        }
        return timeline;
    }

    private int transitionAfter(TripPlanDTO.TimelineNode node) {
        if (node == null || TYPE_STAY_AREA.equals(node.getType())) {
            return 0;
        }
        if (TYPE_LUNCH_AREA.equals(node.getType()) || TYPE_DINNER_AREA.equals(node.getType())) {
            return MEAL_SETTLE_BUFFER_MINUTES;
        }
        if (TYPE_RENTAL_PICKUP.equals(node.getType())) {
            return 10;
        }
        return 0;
    }

    /**
     * 构建「跨天转运」时间线节点（TRANSFER），表示从前一晚酒店前往当天第一个景点的交通段。
     *
     * <p>携带前一天酒店数据（nearbyHotels）和价格标签，供前端在转运节点上展示酒店信息。 根据是否有租车，展示"自驾"或"交通"标签。
     *
     * @param order 节点序号
     * @param time 出发时间（如 "09:00"）
     * @param previousEnd 前一天终点锚点（通常是酒店位置）
     * @param firstSpot 当天第一个景点
     * @param day 当天行程计划
     * @param previousDay 前一天行程计划（携带酒店数据）
     * @param input 时间线输入上下文
     * @return 带有结束时间的转运节点
     */
    private TripPlanDTO.TimelineNode transferNode(
            int order,
            String time,
            TripPlanDTO.Anchor previousEnd,
            TripPlanDTO.Spot firstSpot,
            TripPlanDTO.DailyPlan day,
            TripPlanDTO.DailyPlan previousDay,
            TimelineInput input) {
        // 目的地：取当天首站名称，无则取当天城市
        String to = firstSpot == null ? firstNonBlank(day.getCity(), "今日首站") : firstSpot.getName();
        // 出发地：取前一天终点名称（通常是酒店名），无则默认"酒店"
        String fromHotel = firstNonBlank(previousEnd.getName(), "酒店");
        // 创建紧凑模式的 TRANSFER 节点，标题为"从XX出发"
        TripPlanDTO.TimelineNode node =
                compactNode(order, TYPE_TRANSFER, time, "从" + fromHotel + "出发");
        // 计算从前一天终点到当天首站的交通耗时（分钟）
        int minutes = transferMinutes(previousEnd, firstSpot, day, input);
        // 副标题：描述目的地和交通方式及耗时
        node.setSubtitle(
                "前往"
                        + to
                        + "，"
                        + (hasRental(input) ? "自驾" : "交通")
                        + "约"
                        + readableMinutes(minutes));
        node.setDescription(node.getSubtitle());
        // 将前一天终点坐标赋给该节点（地图标记位置）
        applyAnchor(node, previousEnd);
        node.setDurationMinutes(minutes);
        node.setDurationText("约" + readableMinutes(minutes));
        // 交通建议文案：自驾建议关注路况，非自驾建议打车/公共交通
        node.setTransportSuggestion(hasRental(input) ? "建议按实时路况出发。" : "建议按当天位置选择打车或公共交通。");
        // 标记来源为日程锚点生成
        node.setSource("DAY_ANCHOR");
        node.setFromAnchor(previousEnd.getName());
        node.setToAnchor(to);
        // 将前一天的酒店数据挂到 TRANSFER 节点上，供前端展示酒店地址和价格
        if (previousDay != null
                && previousDay.getNearbyHotels() != null
                && !previousDay.getNearbyHotels().isEmpty()) {
            node.setNearbyHotels(previousDay.getNearbyHotels());
        }
        // 构建标签列表
        List<String> tags = new ArrayList<>();
        // 判断是否跨城（前一天城市和当天城市不同则标"跨城"，否则标"路程"）
        tags.add(crossCity(previousEnd, day) ? "跨城" : "路程");
        // 交通方式标签
        tags.add(hasRental(input) ? "自驾" : "交通");
        // 若前一天有酒店数据，追加价格区间标签
        if (previousDay != null
                && previousDay.getNearbyHotels() != null
                && !previousDay.getNearbyHotels().isEmpty()) {
            String price = previousDay.getNearbyHotels().get(0).getEstimatedPrice();
            if (price != null && !price.isBlank()) {
                tags.add(price);
            }
        }
        node.setTags(tags);
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
            Integer order,
            String time,
            String type,
            TripPlanDTO.DailyPlan day,
            TimelineInput input,
            TripPlanDTO.Spot routeReference) {
        String title = TYPE_LUNCH_AREA.equals(type) ? "午餐时间" : "晚餐时间";
        TripPlanDTO.FoodSuggestion food = mealSuggestion(day, type);
        TripPlanDTO.TimelineNode node =
                compactNode(order, type, time, food == null ? title : food.getName());
        String area =
                firstNonBlank(
                        food == null ? null : firstNonBlank(food.getArea(), food.getName()),
                        firstNonBlank(
                                day.getDiningArea(),
                                firstNonBlank(
                                        routeReference == null ? null : routeReference.getArea(), "就近用餐")));
        node.setSubtitle(area + "附近");
        node.setDescription(
                food == null
                        ? "在" + area + "附近安排用餐，作为区域推荐点展示。"
                        : firstNonBlank(food.getReason(), "按本次修改要求安排用餐。"));
        node.setCity(firstNonBlank(food == null ? null : food.getCity(), day.getCity()));
        node.setArea(area);
        if (food != null) {
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
        int averageCost =
                food != null && food.getAverageCost() != null
                        ? food.getAverageCost()
                        : TYPE_LUNCH_AREA.equals(type) ? 65 : 75;
        node.setEstimatedCost(averageCost * people);
        node.setCostText("约¥" + averageCost + "/人");
        node.setSource(food == null ? "AREA_ESTIMATED" : firstNonBlank(food.getSource(), "AMAP_FOOD"));
        node.setTags(List.of(title, "餐饮"));
        return withEndTime(node);
    }

    private TripPlanDTO.FoodSuggestion mealSuggestion(TripPlanDTO.DailyPlan day, String type) {
        if (day == null || day.getFoodSuggestions() == null || day.getFoodSuggestions().isEmpty()) {
            return null;
        }
        String meal = TYPE_LUNCH_AREA.equals(type) ? "lunch" : "dinner";
        return day.getFoodSuggestions().stream()
                .filter(food -> meal.equalsIgnoreCase(firstNonBlank(food.getMeal(), "")))
                .findFirst()
                .orElse(null);
    }

    /**
     * 构建「住宿区域」时间线节点（STAY_AREA），表示当晚建议住宿的位置。
     *
     * <p>该节点携带酒店价格估算和 nearbyHotels 数据，供前端地图渲染酒店标记和费用展示。
     *
     * @param order 节点在时间线中的序号（递增）
     * @param time 入住时间（如 "20:00"），由 hotelTime() 计算
     * @param day 当天行程计划，用于获取 nearbyHotels 中的价格数据
     * @param hotelAnchor 住宿位置锚点（含区域名、坐标等）
     * @return 带有结束时间的住宿节点
     */
    private TripPlanDTO.TimelineNode hotelNode(
            int order, String time, TripPlanDTO.DailyPlan day, TripPlanDTO.Anchor hotelAnchor) {
        // 创建紧凑模式（compact=true）的 STAY_AREA 类型节点，默认标题为"住宿区域"
        TripPlanDTO.TimelineNode node = compactNode(order, TYPE_STAY_AREA, time, "住宿区域");
        // 副标题：建议住在 XX 区域（取锚点的 area，无则取 name）
        node.setSubtitle("建议住在" + firstNonBlank(hotelAnchor.getArea(), hotelAnchor.getName()));
        // 描述文案：引导用户在该区域住宿，便于衔接下一天
        node.setDescription("今晚建议住在该区域，方便休息并衔接下一天出发。");
        // 将锚点的坐标、城市、区域、地址等信息赋给节点
        applyAnchor(node, hotelAnchor);
        // 标记数据来源为 STAY_AREA，供前端区分节点类型
        node.setSource("STAY_AREA");
        // 若当天已填充附近酒店数据，取第一家酒店的价格信息
        if (day.getNearbyHotels() != null && !day.getNearbyHotels().isEmpty()) {
            TripPlanDTO.NearbyHotel first = day.getNearbyHotels().get(0);
            if (first.getEstimatedCost() != null) {
                // 设置估算费用（元/晚），供预算汇总使用
                node.setEstimatedCost(first.getEstimatedCost());
                // 设置费用展示文案（如"约¥350/晚"），供前端直接展示
                node.setCostText("约¥" + first.getEstimatedCost() + "/晚");
            }
        }
        // 固定标签：住宿区域 + 休息
        node.setTags(List.of("住宿区域", "休息"));
        // 计算并设置结束时间后返回
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
            Integer order, String type, String time, String title) {
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

    /**
     * 确定当晚住宿区域的地理锚点。
     *
     * <p>策略优先级：
     *
     * <ol>
     *   <li>以最后一个景点为基准，尝试朝次日方向偏移（directionalStayAnchor）
     *   <li>回退到最后景点附近的住宿范围
     *   <li>从 DaySkeleton 预规划数据中取住宿/终点/焦点/起点区域
     *   <li>最终兜底：使用餐饮区域或城市名作为住宿区域
     * </ol>
     *
     * @param day 当天行程计划
     * @param input 时间线输入上下文（含骨架、租车等信息）
     * @return 住宿区域锚点（不会返回 null）
     */
    private TripPlanDTO.Anchor hotelAnchor(TripPlanDTO.DailyPlan day, TimelineInput input) {
        // 取当天最后一个景点作为住宿锚点的基准位置
        TripPlanDTO.Spot lastSpot = lastSpot(day);
        // 将最后景点转换为 STAY_AREA 类型的锚点
        TripPlanDTO.Anchor nearLastSpot = spotAnchor(lastSpot, TYPE_STAY_AREA);
        if (nearLastSpot != null) {
            // 尝试朝次日方向偏移住宿位置（若次日焦点较远，住在偏次日方向更合理）
            TripPlanDTO.Anchor directionalStay = directionalStayAnchor(day, nearLastSpot, input);
            if (directionalStay != null) {
                return directionalStay;
            }
            // 偏移不成功，直接用最后景点附近作为住宿范围，名称追加"住宿范围"
            nearLastSpot.setName(
                    firstNonBlank(nearLastSpot.getArea(), nearLastSpot.getName()) + "住宿范围");
            return nearLastSpot;
        }
        // 最后景点无坐标时，尝试从 DaySkeleton 预规划数据中获取住宿锚点
        DaySkeleton skeleton = skeleton(day, input);
        TripPlanDTO.Anchor plannedStay = plannedStayAnchor(skeleton);
        if (plannedStay != null) {
            return plannedStay;
        }
        // 最终兜底：使用餐饮区域或城市名构造一个无坐标的住宿锚点
        TripPlanDTO.Anchor anchor = new TripPlanDTO.Anchor();
        anchor.setType(TYPE_STAY_AREA);
        anchor.setName(firstNonBlank(day.getDiningArea(), firstNonBlank(day.getCity(), "住宿区域")));
        anchor.setCity(day.getCity());
        anchor.setArea(day.getDiningArea());
        return anchor;
    }

    private TripPlanDTO.Anchor plannedStayAnchor(DaySkeleton skeleton) {
        TripPlanDTO.Anchor anchor =
                snapshotAnchor(skeleton == null ? null : skeleton.getStayArea(), TYPE_STAY_AREA);
        if (anchor == null) {
            anchor =
                    snapshotAnchor(skeleton == null ? null : skeleton.getEndArea(), TYPE_STAY_AREA);
        }
        if (anchor == null) {
            anchor =
                    snapshotAnchor(
                            skeleton == null ? null : skeleton.getFocusArea(), TYPE_STAY_AREA);
        }
        if (anchor == null) {
            anchor =
                    snapshotAnchor(
                            skeleton == null ? null : skeleton.getStartArea(), TYPE_STAY_AREA);
        }
        if (anchor != null) {
            anchor.setName(firstNonBlank(anchor.getArea(), anchor.getName()) + "住宿范围");
        }
        return anchor;
    }

    /**
     * 根据次日行程方向，偏移当晚住宿位置。
     *
     * <p>当次日焦点区域距离较远（超过 {@link #NEXT_DAY_DIRECTION_THRESHOLD_KM} = 45km）时， 将住宿位置朝次日方向偏移 {@link
     * #STAY_DIRECTION_SHIFT_RATIO} = 35%， 使第二天出发更近、更省时。
     *
     * @param day 当天行程计划
     * @param nearLastSpot 最后景点附近的锚点（偏移起点）
     * @param input 时间线输入上下文
     * @return 偏移后的住宿锚点；距离不满足条件时返回 null（使用原位置）
     */
    private TripPlanDTO.Anchor directionalStayAnchor(
            TripPlanDTO.DailyPlan day, TripPlanDTO.Anchor nearLastSpot, TimelineInput input) {
        // 获取次日的 DaySkeleton 预规划数据
        DaySkeleton nextSkeleton = skeletonByDay(value(day.getDay()) + 1, input);
        // 从次日骨架中提取焦点区域作为偏移目标
        TripPlanDTO.Anchor nextFocus =
                snapshotAnchor(
                        nextSkeleton == null ? null : nextSkeleton.getFocusArea(), "NEXT_FOCUS");
        // 提取当前锚点和目标锚点的经纬度数组
        double[] from = anchorLocation(nearLastSpot);
        double[] to = anchorLocation(nextFocus);
        // 任一坐标缺失时无法计算偏移，返回 null 使用原位置
        if (from == null || to == null) {
            return null;
        }
        // 计算两点间的直线距离（km）
        double distance = distanceKm(from, to);
        // 距离不足 45km 时无需偏移（次日出发路程不远）
        if (distance < NEXT_DAY_DIRECTION_THRESHOLD_KM) {
            return null;
        }
        // 偏移比例 35%：住在当前和目标之间 35% 的位置
        double ratio = STAY_DIRECTION_SHIFT_RATIO;
        BigDecimal lng = BigDecimal.valueOf(from[0] + (to[0] - from[0]) * ratio);
        BigDecimal lat = BigDecimal.valueOf(from[1] + (to[1] - from[1]) * ratio);
        // 构造偏移后的住宿锚点，名称描述为"XX 至 YY 方向住宿范围"
        return new TripPlanDTO.Anchor(
                TYPE_STAY_AREA,
                firstNonBlank(nearLastSpot.getArea(), nearLastSpot.getName())
                        + "至"
                        + firstNonBlank(nextFocus.getArea(), nextFocus.getName())
                        + "方向住宿范围",
                firstNonBlank(nextFocus.getCity(), nearLastSpot.getCity()),
                firstNonBlank(nearLastSpot.getArea(), nextFocus.getArea()),
                "靠近次日方向，便于第二天出发",
                lng,
                lat,
                "GCJ02");
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
        return skeletonByDay(value(day.getDay()), input);
    }

    private DaySkeleton skeletonByDay(Integer day, TimelineInput input) {
        if (day == null || input == null || input.getDaySkeletons() == null) {
            return null;
        }
        return input.getDaySkeletons().stream()
                .filter(item -> value(item.getDay()).equals(value(day)))
                .findFirst()
                .orElse(null);
    }

    /**
     * 确定每天的时间线结束锚点，优先使用 day.getEndAnchor()， 其次使用 stayAnchorFromTimeline， 兜底策略防止高德拿不到酒店数据
     * stayAnchorFromNearbyHotels。
     *
     * @param day
     * @return
     */
    private TripPlanDTO.Anchor endAnchor(TripPlanDTO.DailyPlan day) {
        // 优先级 1：直接使用 day 上已设置的 endAnchor（assembleDay 中赋值的 hotelAnchor）
        if (day.getEndAnchor() != null) {
            return day.getEndAnchor();
        }
        // 优先级 2：从已组装的时间线中提取 STAY_AREA 节点的坐标（enrichStayAreaNode 已填充）
        TripPlanDTO.Anchor fromTimeline = stayAnchorFromTimeline(day);
        if (fromTimeline != null) {
            return fromTimeline;
        }
        // 优先级 3：从 nearbyHotels 原始数据中提取真实酒店坐标（兜底方案）
        TripPlanDTO.Anchor fromHotels = stayAnchorFromNearbyHotels(day);
        if (fromHotels != null) {
            return fromHotels;
        }
        // 优先级 4：所有兜底都失败，用 hotelAnchor 的纯策略推算（无真实酒店数据）
        return hotelAnchor(day, TimelineInput.empty());
    }

    /** 从已填充酒店坐标的 STAY_AREA 时间线节点中提取锚点（用于跨天传递酒店位置）。 */
    private TripPlanDTO.Anchor stayAnchorFromTimeline(TripPlanDTO.DailyPlan day) {
        // timeline 不存在时直接返回 null
        if (day.getTimeline() == null) {
            return null;
        }
        // 流式遍历时间线，找第一个有坐标的 STAY_AREA 节点
        return day.getTimeline().stream()
                .filter(node -> TYPE_STAY_AREA.equals(node.getType())) // 筛选 STAY_AREA 类型
                .filter(node -> node.getLng() != null && node.getLat() != null) // 必须有坐标
                .findFirst()
                .map(
                        node -> {
                            // 将时间线节点的坐标和地址信息转为 Anchor 对象
                            TripPlanDTO.Anchor anchor = new TripPlanDTO.Anchor();
                            anchor.setType(TYPE_STAY_AREA);
                            anchor.setName(node.getTitle()); // 酒店名称（已被 enrichStayAreaNode 替换为真实酒店名）
                            anchor.setCity(node.getCity());
                            anchor.setArea(node.getArea());
                            anchor.setAddress(node.getAddress());
                            anchor.setLng(node.getLng()); // GCJ02 经度
                            anchor.setLat(node.getLat()); // GCJ02 纬度
                            anchor.setCoordType(node.getCoordType());
                            return anchor;
                        })
                .orElse(null);
    }

    /** 从 nearbyHotels 数据中提取锚点（orchestrator 层填充，用于跨天传递真实酒店名称和坐标）。 */
    private TripPlanDTO.Anchor stayAnchorFromNearbyHotels(TripPlanDTO.DailyPlan day) {
        // nearbyHotels 为空时返回 null
        if (day.getNearbyHotels() == null || day.getNearbyHotels().isEmpty()) {
            return null;
        }
        // 取列表中的第一家酒店（距离最近/评分最高）
        TripPlanDTO.NearbyHotel hotel = day.getNearbyHotels().get(0);
        // 将酒店信息转为 Anchor 锚点对象
        TripPlanDTO.Anchor anchor = new TripPlanDTO.Anchor();
        anchor.setType(TYPE_STAY_AREA);
        anchor.setName(hotel.getName()); // 真实酒店名称（高德 POI）
        anchor.setCity(day.getCity());
        anchor.setArea(hotel.getAddress()); // 酒店地址作为区域描述
        anchor.setAddress(hotel.getAddress());
        anchor.setLng(hotel.getLng()); // GCJ02 经度
        anchor.setLat(hotel.getLat()); // GCJ02 纬度
        anchor.setCoordType(firstNonBlank(hotel.getCoordType(), "GCJ02")); // 坐标系，默认 GCJ02
        return anchor;
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

    private TripPlanDTO.Spot nextSpot(List<TripPlanDTO.Spot> spots, int currentIndex) {
        if (spots == null || currentIndex + 1 >= spots.size()) {
            return null;
        }
        return spots.get(currentIndex + 1);
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

    private boolean canFitVisitBefore(
            int startMinutes, TripPlanDTO.Spot spot, int latestEndMinutes, int transitionMinutes) {
        return canFitVisitBefore(startMinutes, duration(spot), latestEndMinutes, transitionMinutes);
    }

    private boolean canFitVisitBefore(
            int startMinutes, int visitMinutes, int latestEndMinutes, int transitionMinutes) {
        int projectedEnd = startMinutes + visitMinutes + transitionMinutes;
        return projectedEnd <= latestEndMinutes;
    }

    private int visitDurationBeforeWindow(
            int startMinutes, TripPlanDTO.Spot spot, int latestEndMinutes, int transitionMinutes) {
        if (spot == null || startMinutes >= latestEndMinutes) {
            return 0;
        }
        int available = latestEndMinutes - startMinutes - transitionMinutes;
        if (available < MIN_VISIT_MINUTES) {
            return 0;
        }
        return Math.min(duration(spot), available);
    }

    private int transitionMinutes(
            TripPlanDTO.DailyPlan day,
            TripPlanDTO.Spot current,
            TripPlanDTO.Spot next,
            TimelineInput input,
            boolean beforeMeal) {
        if (next == null) {
            return beforeMeal ? lunchTransferBuffer(input) : nightTransferBuffer(input);
        }
        Integer routeMinutes = routeLegMinutes(day, current, next);
        int base = routeMinutes == null ? routeBuffer(input) : routeMinutes;
        return Math.max(15, base + 8);
    }

    private Integer routeLegMinutes(
            TripPlanDTO.DailyPlan day, TripPlanDTO.Spot current, TripPlanDTO.Spot next) {
        if (day == null
                || day.getRouteLegs() == null
                || current == null
                || next == null
                || current.getOrder() == null
                || next.getOrder() == null) {
            return null;
        }
        return day.getRouteLegs().stream()
                .filter(leg -> current.getOrder().equals(leg.getFromOrder()))
                .filter(leg -> next.getOrder().equals(leg.getToOrder()))
                .map(TripPlanDTO.RouteLeg::getDurationMinutes)
                .filter(value -> value != null && value > 0)
                .findFirst()
                .orElse(null);
    }

    private int lunchTransferBuffer(TimelineInput input) {
        return hasRental(input) ? 25 : 18;
    }

    private int nightTransferBuffer(TimelineInput input) {
        return hasRental(input) ? 25 : 20;
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

    private double[] anchorLocation(TripPlanDTO.Anchor anchor) {
        if (anchor == null || anchor.getLng() == null || anchor.getLat() == null) {
            return null;
        }
        return new double[] {anchor.getLng().doubleValue(), anchor.getLat().doubleValue()};
    }

    private double distanceKm(double[] from, double[] to) {
        if (from == null || to == null) {
            return Double.MAX_VALUE;
        }
        return com.sora.aitravel.service.route.GeoRouteCalculator.distanceKm(
                from[0], from[1], to[0], to[1]);
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
        String normalizedRange = range.toLowerCase();
        if (normalizedRange.contains("中午")
                || normalizedRange.contains("午间")
                || normalizedRange.contains("noon")) {
            return "12:30";
        }
        if (normalizedRange.contains("下午")
                || normalizedRange.contains("傍晚")
                || normalizedRange.contains("afternoon")) {
            return "14:30";
        }
        if (normalizedRange.contains("晚上")
                || normalizedRange.contains("夜间")
                || normalizedRange.contains("夜里")
                || normalizedRange.contains("night")) {
            return "18:30";
        }
        return "09:30";
    }

    private String lunchTime(int cursor) {
        return formatTime(Math.max(cursor, LUNCH_EARLIEST));
    }

    private String dinnerTime(int cursor) {
        return formatTime(Math.max(cursor, DINNER_EARLIEST));
    }

    private String hotelTime(int cursor) {
        return formatTime(cursor);
    }

    private boolean shouldAddLunch(int cursor, int completedMorningSpots) {
        return completedMorningSpots > 0 || cursor <= LUNCH_LATEST;
    }

    private boolean shouldAddDinner(
            int cursor, List<TripPlanDTO.TimelineNode> timeline, List<TripPlanDTO.Spot> nightSpots) {
        if (!canFitVisitBefore(
                cursor, DINNER_DURATION_MINUTES, NIGHT_END_LIMIT, MEAL_SETTLE_BUFFER_MINUTES)) {
            return false;
        }
        boolean hasAfternoonActivity =
                timeline != null
                        && timeline.stream()
                                .filter(node -> node.getStartTime() != null)
                                .filter(node -> !isUtilityTimelineType(node.getType()))
                                .anyMatch(node -> parseTime(node.getStartTime()) >= LUNCH_EARLIEST);
        boolean hasNightActivity = nightSpots != null && !nightSpots.isEmpty();
        return hasAfternoonActivity || hasNightActivity;
    }

    private boolean isUtilityTimelineType(String type) {
        return TYPE_DAY_START.equals(type)
                || TYPE_TRANSFER.equals(type)
                || TYPE_RENTAL_PICKUP.equals(type)
                || TYPE_CAR_RETURN_SERVICE.equals(type)
                || TYPE_LUNCH_AREA.equals(type)
                || TYPE_DINNER_AREA.equals(type)
                || TYPE_STAY_AREA.equals(type);
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
