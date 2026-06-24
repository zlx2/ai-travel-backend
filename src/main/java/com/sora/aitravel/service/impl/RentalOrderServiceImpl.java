package com.sora.aitravel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.dto.model.RentalFeeBreakdownDTO;
import com.sora.aitravel.dto.model.RentalQuoteOptionDTO;
import com.sora.aitravel.dto.model.RentalRequirementDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.model.TripPlanDTO;
import com.sora.aitravel.dto.request.RentalOrderCreateRequest;
import com.sora.aitravel.dto.request.RentalOrderPayRequest;
import com.sora.aitravel.dto.response.RentalOrderResponse;
import com.sora.aitravel.entity.RentalOrder;
import com.sora.aitravel.entity.Trip;
import com.sora.aitravel.mapper.RentalOrderMapper;
import com.sora.aitravel.mapper.TripMapper;
import com.sora.aitravel.service.RentalOrderService;
import com.sora.aitravel.service.RentalQuoteService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RentalOrderServiceImpl implements RentalOrderService {
    private final RentalOrderMapper rentalOrderMapper;
    private final TripMapper tripMapper;
    private final RentalQuoteService rentalQuoteService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public Long create(Long userId, RentalOrderCreateRequest request) {
        RentalQuoteOptionDTO quote =
                rentalQuoteService.recalculate(
                        request.getRequirement(), request.getSelectedQuote());
        if (!sameQuote(request.getSelectedQuote(), quote)) {
            throw new BusinessException(ErrorCode.CONFLICT, "所选车型报价已变化，请重新选择报价");
        }
        Trip trip = buildTrip(userId, request, quote);
        tripMapper.insert(trip);

        RentalOrder order = buildOrder(userId, trip.getId(), request, quote);
        rentalOrderMapper.insert(order);
        return order.getId();
    }

    @Override
    public void pay(Long userId, Long id, RentalOrderPayRequest request) {
        RentalOrder order = mustGetOwnedOrder(userId, id);
        if (!"pending".equals(order.getOrderStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "只有待支付订单可以支付");
        }
        boolean success =
                request == null
                        || request.getSuccess() == null
                        || Boolean.TRUE.equals(request.getSuccess());
        if (!success) {
            return;
        }
        order.setPaymentStatus("paid");
        order.setOrderStatus("confirmed");
        rentalOrderMapper.updateById(order);
    }

    @Override
    public List<RentalOrderResponse> listMy(Long userId) {
        return rentalOrderMapper
                .selectList(
                        new LambdaQueryWrapper<RentalOrder>()
                                .eq(RentalOrder::getUserId, userId)
                                .orderByDesc(RentalOrder::getCreateTime))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public RentalOrderResponse get(Long userId, Long id) {
        return toResponse(mustGetOwnedOrder(userId, id));
    }

    @Override
    public void cancel(Long userId, Long id) {
        RentalOrder order = mustGetOwnedOrder(userId, id);
        if ("completed".equals(order.getOrderStatus())
                || "cancelled".equals(order.getOrderStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前订单状态不可取消");
        }
        order.setOrderStatus("cancelled");
        rentalOrderMapper.updateById(order);
    }

    private Trip buildTrip(
            Long userId, RentalOrderCreateRequest request, RentalQuoteOptionDTO quote) {
        TravelRequirementDTO requirement = request.getRequirement();
        TripPlanDTO tripPlan = request.getTripPlan();
        Trip trip = new Trip();
        trip.setUserId(userId);
        trip.setConversationId(request.getConversationId());
        trip.setTitle(
                notBlank(tripPlan.getTitle())
                        ? tripPlan.getTitle()
                        : tripDestination(requirement, tripPlan) + "行程");
        trip.setDeparture(tripDeparture(requirement, quote));
        trip.setDestination(tripDestination(requirement, tripPlan));
        trip.setDays(requirement.getDays());
        trip.setBudget(requirement.getBudget());
        trip.setPreferencesJson(toJson(requirement.getPreferences()));
        trip.setRequirementJson(toJson(requirement));
        trip.setTripPlanJson(toJson(tripPlan));
        trip.setSummary(tripPlan.getSummary());
        trip.setSource(1);
        trip.setStatus(1);
        trip.setDeleted(0);
        return trip;
    }

    private RentalOrder buildOrder(
            Long userId,
            Long tripId,
            RentalOrderCreateRequest request,
            RentalQuoteOptionDTO quote) {
        TravelRequirementDTO requirement = request.getRequirement();
        RentalRequirementDTO rental = requirement.getRentalRequirement();
        RentalFeeBreakdownDTO fee = quote.getFeeBreakdown();
        LocalDate pickupDate = parseDate(requirement.getTravelDate());

        RentalOrder order = new RentalOrder();
        order.setOrderNo(
                "RO"
                        + LocalDateTime.now()
                                .format(
                                        java.time.format.DateTimeFormatter.ofPattern(
                                                "yyyyMMddHHmmss"))
                        + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        order.setUserId(userId);
        order.setTripId(tripId);
        order.setPickupPoiId(quote.getPickupPoiId());
        order.setPickupMode(quote.getPickupMode());
        order.setReturnPoiId(quote.getReturnPoiId());
        order.setReturnMode(quote.getReturnMode());
        order.setDeliveryAddress(rental == null ? null : rental.getDeliveryAddress());
        order.setReturnAddress(rental == null ? null : rental.getReturnAddress());
        order.setDeliveryFeeCent(fee.getDeliveryFeeCent());
        order.setPickupPoiSnapshot(toJson(poiSnapshot("pickup", quote)));
        order.setReturnPoiSnapshot(toJson(poiSnapshot("return", quote)));
        order.setVehicleGroupId(quote.getVehicleGroupId());
        order.setPickupTime(pickupDate.atTime(10, 0));
        order.setReturnTime(pickupDate.plusDays(quote.getRentalDays()).atTime(18, 0));
        order.setRentalDays(BigDecimal.valueOf(quote.getRentalDays()));
        order.setIsOneWay(Boolean.TRUE.equals(quote.getIsOneWay()) ? 1 : 0);
        order.setRentalFeeCent(fee.getRentalFeeCent());
        order.setBaseServiceFeeCent(fee.getBaseServiceFeeCent());
        order.setVehiclePrepareFeeCent(fee.getVehiclePrepareFeeCent());
        order.setOneWayBaseFeeCent(fee.getOneWayFeeCent());
        order.setOneWayDiscountCent(0);
        order.setOneWayFinalFeeCent(fee.getOneWayFeeCent());
        order.setRentalDepositCent(fee.getRentalDepositCent());
        order.setViolationDepositCent(fee.getViolationDepositCent());
        order.setDepositFreeThresholdScore(fee.getDepositFreeThresholdScore());
        order.setTotalPriceCent(fee.getTotalPriceCent());
        order.setPriceTemplateId(quote.getPriceTemplateId());
        order.setPriceSnapshot(toJson(quote.getPriceSnapshot()));
        order.setContactName(request.getContactName());
        order.setContactPhone(request.getContactPhone());
        order.setOrderStatus("pending");
        order.setPaymentStatus("unpaid");
        order.setRemark(request.getRemark());
        order.setDeleted(0);
        return order;
    }

    private Map<String, Object> poiSnapshot(String type, RentalQuoteOptionDTO quote) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("type", type);
        snapshot.put("rentalCity", quote.getRentalCity());
        snapshot.put("citycode", quote.getCitycode());
        if ("pickup".equals(type)) {
            snapshot.put("poiId", quote.getPickupPoiId());
            snapshot.put("poiName", quote.getPickupPoiName());
            snapshot.put("address", quote.getPickupAddress());
            snapshot.put("mode", quote.getPickupMode());
        } else {
            snapshot.put("poiId", quote.getReturnPoiId());
            snapshot.put("poiName", quote.getReturnPoiName());
            snapshot.put("address", quote.getReturnAddress());
            snapshot.put("mode", quote.getReturnMode());
        }
        return snapshot;
    }

    private RentalOrder mustGetOwnedOrder(Long userId, Long id) {
        RentalOrder order = rentalOrderMapper.selectById(id);
        if (order == null || !userId.equals(order.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "租车订单不存在");
        }
        return order;
    }

    private RentalOrderResponse toResponse(RentalOrder order) {
        RentalFeeBreakdownDTO fee =
                new RentalFeeBreakdownDTO(
                        order.getRentalFeeCent(),
                        order.getBaseServiceFeeCent(),
                        order.getVehiclePrepareFeeCent(),
                        order.getOneWayFinalFeeCent(),
                        order.getDeliveryFeeCent(),
                        order.getTotalPriceCent(),
                        order.getRentalDepositCent(),
                        order.getViolationDepositCent(),
                        order.getDepositFreeThresholdScore());
        Map<String, Object> priceSnapshot = fromJsonMap(order.getPriceSnapshot());
        return new RentalOrderResponse(
                order.getId(),
                order.getOrderNo(),
                order.getTripId(),
                stringValue(priceSnapshot.get("city")),
                order.getVehicleGroupId(),
                order.getOrderStatus(),
                order.getPaymentStatus(),
                order.getTotalPriceCent(),
                fee,
                fromJsonMap(order.getPickupPoiSnapshot()),
                fromJsonMap(order.getReturnPoiSnapshot()),
                priceSnapshot,
                order.getPickupTime(),
                order.getReturnTime(),
                order.getCreateTime());
    }

    private String tripDestination(TravelRequirementDTO requirement, TripPlanDTO tripPlan) {
        if (notBlank(requirement.getDestination())) {
            return requirement.getDestination();
        }
        if (requirement.getRouteCities() != null && !requirement.getRouteCities().isEmpty()) {
            return String.join("-", requirement.getRouteCities());
        }
        if (notBlank(requirement.getRouteRegion())) {
            return requirement.getRouteRegion();
        }
        return tripPlan.getDestination();
    }

    private String tripDeparture(TravelRequirementDTO requirement, RentalQuoteOptionDTO quote) {
        if (notBlank(requirement.getDeparture())) {
            return requirement.getDeparture();
        }
        if (notBlank(quote.getRentalCity())) {
            return quote.getRentalCity();
        }
        return "未知出发地";
    }

    private LocalDate parseDate(String value) {
        try {
            return value == null || value.isBlank()
                    ? LocalDate.now().plusDays(1)
                    : LocalDate.parse(value);
        } catch (Exception e) {
            return LocalDate.now().plusDays(1);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "JSON 序列化失败");
        }
    }

    private Map<String, Object> fromJsonMap(String json) {
        try {
            if (json == null || json.isBlank()) {
                return Map.of();
            }
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private boolean sameQuote(
            RentalQuoteOptionDTO selectedQuote, RentalQuoteOptionDTO recalculatedQuote) {
        return equalsValue(selectedQuote.getVehicleGroupId(), recalculatedQuote.getVehicleGroupId())
                && equalsValue(
                        selectedQuote.getPriceTemplateId(), recalculatedQuote.getPriceTemplateId())
                && equalsValue(selectedQuote.getPickupPoiId(), recalculatedQuote.getPickupPoiId())
                && equalsValue(selectedQuote.getReturnPoiId(), recalculatedQuote.getReturnPoiId())
                && equalsValue(selectedQuote.getRentalDays(), recalculatedQuote.getRentalDays())
                && equalsValue(totalPrice(selectedQuote), totalPrice(recalculatedQuote));
    }

    private Integer totalPrice(RentalQuoteOptionDTO quote) {
        return quote.getFeeBreakdown() == null ? null : quote.getFeeBreakdown().getTotalPriceCent();
    }

    private boolean equalsValue(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }
}
