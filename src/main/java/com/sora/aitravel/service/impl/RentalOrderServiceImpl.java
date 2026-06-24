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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RentalOrderServiceImpl implements RentalOrderService {
    private final RentalOrderMapper rentalOrderMapper;
    private final TripMapper tripMapper;
    private final RentalQuoteService rentalQuoteService;
    private final ObjectMapper objectMapper;

    public RentalOrderServiceImpl(
            RentalOrderMapper rentalOrderMapper,
            TripMapper tripMapper,
            RentalQuoteService rentalQuoteService,
            ObjectMapper objectMapper) {
        this.rentalOrderMapper = rentalOrderMapper;
        this.tripMapper = tripMapper;
        this.rentalQuoteService = rentalQuoteService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public Long create(Long userId, RentalOrderCreateRequest request) {
        RentalQuoteOptionDTO quote =
                rentalQuoteService.recalculate(request.requirement(), request.selectedQuote());
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
            throw new BusinessException(ErrorCode.CONFLICT, "只有待确认订单可以支付");
        }
        boolean success =
                request == null
                        || request.success() == null
                        || Boolean.TRUE.equals(request.success());
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
        TravelRequirementDTO requirement = request.requirement();
        TripPlanDTO tripPlan = request.tripPlan();
        Trip trip = new Trip();
        trip.setUserId(userId);
        trip.setConversationId(request.conversationId());
        trip.setTitle(tripPlan.title());
        trip.setDeparture(requirement.departure());
        trip.setDestination(tripDestination(requirement, tripPlan));
        trip.setDays(requirement.days());
        trip.setBudget(requirement.budget());
        trip.setPreferencesJson(toJson(requirement.preferences()));
        trip.setRequirementJson(toJson(requirement));
        trip.setTripPlanJson(toJson(tripPlan));
        trip.setSummary(tripPlan.summary());
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
        TravelRequirementDTO requirement = request.requirement();
        RentalRequirementDTO rental = requirement.rentalRequirement();
        RentalFeeBreakdownDTO fee = quote.feeBreakdown();
        LocalDate pickupDate = parseDate(requirement.travelDate());

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
        order.setPickupPoiId(quote.pickupPoiId());
        order.setPickupMode(quote.pickupMode());
        order.setReturnPoiId(quote.returnPoiId());
        order.setReturnMode(quote.returnMode());
        order.setDeliveryAddress(rental == null ? null : rental.deliveryAddress());
        order.setReturnAddress(rental == null ? null : rental.returnAddress());
        order.setDeliveryFeeCent(fee.deliveryFeeCent());
        order.setPickupPoiSnapshot(toJson(poiSnapshot("pickup", quote)));
        order.setReturnPoiSnapshot(toJson(poiSnapshot("return", quote)));
        order.setVehicleGroupId(quote.vehicleGroupId());
        order.setPickupTime(pickupDate.atTime(10, 0));
        order.setReturnTime(pickupDate.plusDays(quote.rentalDays()).atTime(18, 0));
        order.setRentalDays(BigDecimal.valueOf(quote.rentalDays()));
        order.setIsOneWay(Boolean.TRUE.equals(quote.isOneWay()) ? 1 : 0);
        order.setRentalFeeCent(fee.rentalFeeCent());
        order.setBaseServiceFeeCent(fee.baseServiceFeeCent());
        order.setVehiclePrepareFeeCent(fee.vehiclePrepareFeeCent());
        order.setOneWayBaseFeeCent(fee.oneWayFeeCent());
        order.setOneWayDiscountCent(0);
        order.setOneWayFinalFeeCent(fee.oneWayFeeCent());
        order.setRentalDepositCent(fee.rentalDepositCent());
        order.setViolationDepositCent(fee.violationDepositCent());
        order.setDepositFreeThresholdScore(fee.depositFreeThresholdScore());
        order.setTotalPriceCent(fee.totalPriceCent());
        order.setPriceTemplateId(quote.priceTemplateId());
        order.setPriceSnapshot(toJson(quote.priceSnapshot()));
        order.setContactName(request.contactName());
        order.setContactPhone(request.contactPhone());
        order.setOrderStatus("pending");
        order.setPaymentStatus("unpaid");
        order.setRemark(request.remark());
        order.setDeleted(0);
        return order;
    }

    private Map<String, Object> poiSnapshot(String type, RentalQuoteOptionDTO quote) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("type", type);
        snapshot.put("rentalCity", quote.rentalCity());
        snapshot.put("citycode", quote.citycode());
        if ("pickup".equals(type)) {
            snapshot.put("poiId", quote.pickupPoiId());
            snapshot.put("poiName", quote.pickupPoiName());
            snapshot.put("address", quote.pickupAddress());
            snapshot.put("mode", quote.pickupMode());
        } else {
            snapshot.put("poiId", quote.returnPoiId());
            snapshot.put("poiName", quote.returnPoiName());
            snapshot.put("address", quote.returnAddress());
            snapshot.put("mode", quote.returnMode());
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
        if (notBlank(requirement.destination())) {
            return requirement.destination();
        }
        if (requirement.routeCities() != null && !requirement.routeCities().isEmpty()) {
            return String.join("-", requirement.routeCities());
        }
        if (notBlank(requirement.routeRegion())) {
            return requirement.routeRegion();
        }
        return tripPlan.destination();
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
}
