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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
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
        long startedAt = System.currentTimeMillis();
        validateCreateRequest(userId, request);
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
        log.info(
                "租车订单创建完成，userId={}, orderId={}, orderNo={}, tripId={}, vehicleGroupId={}, totalPriceCent={}, elapsedMs={}",
                userId,
                order.getId(),
                order.getOrderNo(),
                trip.getId(),
                order.getVehicleGroupId(),
                order.getTotalPriceCent(),
                System.currentTimeMillis() - startedAt);
        return order.getId();
    }

    @Override
    public void pay(Long userId, Long id, RentalOrderPayRequest request) {
        validatePayRequest(userId, id);
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
        log.info("租车订单模拟支付完成，userId={}, orderId={}, orderNo={}", userId, id, order.getOrderNo());
    }

    private void validateCreateRequest(Long userId, RentalOrderCreateRequest request) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "租车订单请求不能为空");
        }
        if (request.getRequirement() == null
                || request.getTripPlan() == null
                || request.getSelectedQuote() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "下单必须包含行程需求、行程方案和已选报价");
        }
        if (request.getRequirement().getDays() == null || request.getRequirement().getDays() < 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "行程天数不合法");
        }
    }

    private void validatePayRequest(Long userId, Long id) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "订单 ID 不合法");
        }
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
        return Trip.builder()
                .userId(userId)
                .conversationId(request.getConversationId())
                .title(
                        notBlank(tripPlan.getTitle())
                                ? tripPlan.getTitle()
                                : tripDestination(requirement, tripPlan) + "行程")
                .departure(tripDeparture(requirement, quote))
                .destination(tripDestination(requirement, tripPlan))
                .days(requirement.getDays())
                .budget(requirement.getBudget())
                .preferencesJson(toJson(requirement.getPreferences()))
                .requirementJson(toJson(requirement))
                .tripPlanJson(toJson(tripPlan))
                .summary(tripPlan.getSummary())
                .source(1)
                .status(1)
                .deleted(0)
                .build();
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

        return RentalOrder.builder()
                .orderNo(
                        "RO"
                                + LocalDateTime.now()
                                        .format(
                                                java.time.format.DateTimeFormatter.ofPattern(
                                                        "yyyyMMddHHmmss"))
                                + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                .userId(userId)
                .tripId(tripId)
                .pickupPoiId(quote.getPickupPoiId())
                .pickupMode(quote.getPickupMode())
                .returnPoiId(quote.getReturnPoiId())
                .returnMode(quote.getReturnMode())
                .deliveryAddress(rental == null ? null : rental.getDeliveryAddress())
                .returnAddress(rental == null ? null : rental.getReturnAddress())
                .deliveryFeeCent(fee.getDeliveryFeeCent())
                .pickupPoiSnapshot(toJson(poiSnapshot("pickup", quote)))
                .returnPoiSnapshot(toJson(poiSnapshot("return", quote)))
                .vehicleGroupId(quote.getVehicleGroupId())
                .pickupTime(pickupDate.atTime(10, 0))
                .returnTime(pickupDate.plusDays(quote.getRentalDays()).atTime(18, 0))
                .rentalDays(BigDecimal.valueOf(quote.getRentalDays()))
                .isOneWay(Boolean.TRUE.equals(quote.getIsOneWay()) ? 1 : 0)
                .rentalFeeCent(fee.getRentalFeeCent())
                .baseServiceFeeCent(fee.getBaseServiceFeeCent())
                .vehiclePrepareFeeCent(fee.getVehiclePrepareFeeCent())
                .oneWayBaseFeeCent(fee.getOneWayFeeCent())
                .oneWayDiscountCent(0)
                .oneWayFinalFeeCent(fee.getOneWayFeeCent())
                .rentalDepositCent(fee.getRentalDepositCent())
                .violationDepositCent(fee.getViolationDepositCent())
                .depositFreeThresholdScore(fee.getDepositFreeThresholdScore())
                .totalPriceCent(
                        value(fee.getTotalPriceCent()) + value(request.getProtectionFeeCent()))
                .priceTemplateId(quote.getPriceTemplateId())
                .priceSnapshot(toJson(quote.getPriceSnapshot()))
                .contactName(request.getContactName())
                .contactPhone(request.getContactPhone())
                .protectionPackageCode(request.getProtectionPackageCode())
                .protectionPackageName(request.getProtectionPackageName())
                .protectionFeeCent(value(request.getProtectionFeeCent()))
                .orderStatus("pending")
                .paymentStatus("unpaid")
                .remark(request.getRemark())
                .deleted(0)
                .build();
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
                RentalFeeBreakdownDTO.builder()
                        .rentalFeeCent(order.getRentalFeeCent())
                        .baseServiceFeeCent(order.getBaseServiceFeeCent())
                        .vehiclePrepareFeeCent(order.getVehiclePrepareFeeCent())
                        .oneWayFeeCent(order.getOneWayFinalFeeCent())
                        .deliveryFeeCent(order.getDeliveryFeeCent())
                        .totalPriceCent(order.getTotalPriceCent())
                        .rentalDepositCent(order.getRentalDepositCent())
                        .violationDepositCent(order.getViolationDepositCent())
                        .depositFreeThresholdScore(order.getDepositFreeThresholdScore())
                        .build();
        Map<String, Object> priceSnapshot = fromJsonMap(order.getPriceSnapshot());
        return RentalOrderResponse.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .tripId(order.getTripId())
                .rentalCity(stringValue(priceSnapshot.get("city")))
                .vehicleGroupId(order.getVehicleGroupId())
                .orderStatus(order.getOrderStatus())
                .paymentStatus(order.getPaymentStatus())
                .totalPriceCent(order.getTotalPriceCent())
                .protectionPackageCode(order.getProtectionPackageCode())
                .protectionPackageName(order.getProtectionPackageName())
                .protectionFeeCent(order.getProtectionFeeCent())
                .feeBreakdown(fee)
                .pickupPoiSnapshot(fromJsonMap(order.getPickupPoiSnapshot()))
                .returnPoiSnapshot(fromJsonMap(order.getReturnPoiSnapshot()))
                .priceSnapshot(priceSnapshot)
                .pickupTime(order.getPickupTime())
                .returnTime(order.getReturnTime())
                .createTime(order.getCreateTime())
                .build();
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

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean equalsValue(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }
}
