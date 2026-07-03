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

/**
 * 租车订单服务实现类
 * 处理租车订单的创建、支付、查询、取消等业务逻辑
 * 
 * <p>核心功能：
 * <ul>
 *   <li>订单创建：校验请求参数，重新计算报价，创建行程和订单记录</li>
 *   <li>订单支付：更新订单支付状态和订单状态</li>
 *   <li>订单查询：查询用户的租车订单列表和单个订单详情</li>
 *   <li>订单取消：取消待支付或待确认状态的订单</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RentalOrderServiceImpl implements RentalOrderService {
    
    private final RentalOrderMapper rentalOrderMapper;
    private final TripMapper tripMapper;
    private final RentalQuoteService rentalQuoteService;
    private final ObjectMapper objectMapper;

    /**
     * 创建租车订单
     * 
     * <p>业务流程：
     * <ol>
     *   <li>校验用户ID和请求参数</li>
     *   <li>调用报价服务重新计算报价，确保价格未变化</li>
     *   <li>合并用户确认的报价和重新计算的报价</li>
     *   <li>创建行程记录</li>
     *   <li>创建租车订单记录</li>
     * </ol>
     *
     * @param userId  用户ID
     * @param request 订单创建请求，包含行程需求、行程方案和已选报价
     * @return 创建的订单ID
     * @throws BusinessException 当参数校验失败、报价已变化或系统错误时抛出
     */
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
        log.info(
                "租车报价核心校验通过，动态取还车点/费用按用户确认快照下单。selectedQuote={}, recalculatedQuote={}",
                request.getSelectedQuote().getQuoteId(),
                quote.getQuoteId());
        quote = mergeUserConfirmedQuote(request.getSelectedQuote(), quote);
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

    /**
     * 支付租车订单
     * 
     * <p>业务流程：
     * <ol>
     *   <li>校验用户ID和订单ID</li>
     *   <li>获取用户拥有的订单</li>
     *   <li>检查订单状态是否为待支付</li>
     *   <li>如果支付成功，更新订单状态为已支付和已确认</li>
     * </ol>
     *
     * @param userId 用户ID
     * @param id     订单ID
     * @param request 支付请求，包含支付成功标识
     * @throws BusinessException 当参数校验失败、订单不存在、订单状态不允许支付时抛出
     */
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

    /**
     * 校验订单创建请求参数
     * 
     * <p>校验规则：
     * <ul>
     *   <li>用户ID不能为空</li>
     *   <li>请求对象不能为空</li>
     *   <li>必须包含行程需求、行程方案和已选报价</li>
     *   <li>行程天数必须大于0</li>
     * </ul>
     *
     * @param userId  用户ID
     * @param request 订单创建请求
     * @throws BusinessException 当校验不通过时抛出
     */
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

    /**
     * 校验订单支付请求参数
     * 
     * <p>校验规则：
     * <ul>
     *   <li>用户ID不能为空</li>
     *   <li>订单ID必须大于0</li>
     * </ul>
     *
     * @param userId 用户ID
     * @param id     订单ID
     * @throws BusinessException 当校验不通过时抛出
     */
    private void validatePayRequest(Long userId, Long id) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "订单 ID 不合法");
        }
    }

    /**
     * 查询用户的租车订单列表
     * 
     * <p>查询条件：
     * <ul>
     *   <li>按用户ID筛选</li>
     *   <li>按创建时间降序排列</li>
     * </ul>
     *
     * @param userId 用户ID
     * @return 租车订单响应列表
     */
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

    /**
     * 查询单个租车订单详情
     * 
     * <p>校验规则：
     * <ul>
     *   <li>订单必须存在</li>
     *   <li>订单必须属于当前用户</li>
     * </ul>
     *
     * @param userId 用户ID
     * @param id     订单ID
     * @return 租车订单响应
     * @throws BusinessException 当订单不存在或不属于当前用户时抛出
     */
    @Override
    public RentalOrderResponse get(Long userId, Long id) {
        return toResponse(mustGetOwnedOrder(userId, id));
    }

    /**
     * 取消租车订单
     * 
     * <p>业务规则：
     * <ul>
     *   <li>订单必须属于当前用户</li>
     *   <li>订单状态不能是已完成或已取消</li>
     * </ul>
     *
     * @param userId 用户ID
     * @param id     订单ID
     * @throws BusinessException 当订单不存在、不属于当前用户或状态不允许取消时抛出
     */
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

    /**
     * 构建行程实体
     * 
     * <p>从订单创建请求中提取行程信息，构建Trip实体对象
     *
     * @param userId  用户ID
     * @param request 订单创建请求
     * @param quote   报价信息
     * @return 行程实体
     */
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

    /**
     * 构建租车订单实体
     * 
     * <p>从订单创建请求和报价信息中提取数据，构建RentalOrder实体对象
     *
     * @param userId  用户ID
     * @param tripId  行程ID
     * @param request 订单创建请求
     * @param quote   报价信息
     * @return 租车订单实体
     */
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

    /**
     * 生成POI快照
     * 
     * <p>根据类型（取车/还车）从报价中提取POI信息，生成快照用于订单记录
     *
     * @param type  POI类型（pickup/return）
     * @param quote 报价信息
     * @return POI快照Map
     */
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

    /**
     * 获取用户拥有的订单
     * 
     * <p>校验规则：
     * <ul>
     *   <li>订单必须存在</li>
     *   <li>订单必须属于当前用户</li>
     * </ul>
     *
     * @param userId 用户ID
     * @param id     订单ID
     * @return 租车订单实体
     * @throws BusinessException 当订单不存在或不属于当前用户时抛出
     */
    private RentalOrder mustGetOwnedOrder(Long userId, Long id) {
        RentalOrder order = rentalOrderMapper.selectById(id);
        if (order == null || !userId.equals(order.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "租车订单不存在");
        }
        return order;
    }

    /**
     * 将租车订单实体转换为响应对象
     * 
     * <p>将数据库实体转换为API响应格式，包含费用明细、取还车点快照等信息
     *
     * @param order 租车订单实体
     * @return 租车订单响应
     */
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

    /**
     * 获取行程目的地
     * 
     * <p>优先级：
     * <ol>
     *   <li>需求中的目的地</li>
     *   <li>需求中的路线城市列表（用"-"连接）</li>
     *   <li>需求中的路线区域</li>
     *   <li>行程方案中的目的地</li>
     * </ol>
     *
     * @param requirement 旅行需求
     * @param tripPlan    行程方案
     * @return 目的地名称
     */
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

    /**
     * 获取行程出发地
     * 
     * <p>优先级：
     * <ol>
     *   <li>需求中的出发地</li>
     *   <li>报价中的租车城市</li>
     *   <li>默认值"未知出发地"</li>
     * </ol>
     *
     * @param requirement 旅行需求
     * @param quote       报价信息
     * @return 出发地名称
     */
    private String tripDeparture(TravelRequirementDTO requirement, RentalQuoteOptionDTO quote) {
        if (notBlank(requirement.getDeparture())) {
            return requirement.getDeparture();
        }
        if (notBlank(quote.getRentalCity())) {
            return quote.getRentalCity();
        }
        return "未知出发地";
    }

    /**
     * 解析日期字符串
     * 
     * <p>解析规则：
     * <ul>
     *   <li>空值或空白字符串：返回明天日期</li>
     *   <li>有效日期格式：解析为LocalDate</li>
     *   <li>解析失败：返回明天日期</li>
     * </ul>
     *
     * @param value 日期字符串（yyyy-MM-dd格式）
     * @return LocalDate对象
     */
    private LocalDate parseDate(String value) {
        try {
            return value == null || value.isBlank()
                    ? LocalDate.now().plusDays(1)
                    : LocalDate.parse(value);
        } catch (Exception e) {
            return LocalDate.now().plusDays(1);
        }
    }

    /**
     * 对象转换为JSON字符串
     *
     * @param value 待转换的对象
     * @return JSON字符串
     * @throws BusinessException 当序列化失败时抛出
     */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "JSON 序列化失败");
        }
    }

    /**
     * JSON字符串转换为Map
     *
     * @param json JSON字符串
     * @return Map对象，解析失败返回空Map
     */
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

    /**
     * 对象转换为字符串
     *
     * @param value 待转换的对象
     * @return 字符串值，null返回null
     */
    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 判断字符串是否非空
     *
     * @param value 字符串值
     * @return true表示非空，false表示为空
     */
    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 判断两个报价是否相同
     * 
     * <p>比较维度：
     * <ul>
     *   <li>车型组ID</li>
     *   <li>价格模板ID</li>
     *   <li>租车天数</li>
     * </ul>
     *
     * @param selectedQuote     用户选择的报价
     * @param recalculatedQuote 重新计算的报价
     * @return true表示相同，false表示不同
     */
    private boolean sameQuote(
            RentalQuoteOptionDTO selectedQuote, RentalQuoteOptionDTO recalculatedQuote) {
        return equalsValue(selectedQuote.getVehicleGroupId(), recalculatedQuote.getVehicleGroupId())
                && equalsValue(
                        selectedQuote.getPriceTemplateId(), recalculatedQuote.getPriceTemplateId())
                && equalsValue(selectedQuote.getRentalDays(), recalculatedQuote.getRentalDays());
    }

    /**
     * 合并用户确认的报价和重新计算的报价
     * 
     * <p>合并规则：以用户确认的报价为主，缺失的字段从重新计算的报价中补充
     *
     * @param selectedQuote     用户选择的报价
     * @param recalculatedQuote 重新计算的报价
     * @return 合并后的报价
     */
    private RentalQuoteOptionDTO mergeUserConfirmedQuote(
            RentalQuoteOptionDTO selectedQuote, RentalQuoteOptionDTO recalculatedQuote) {
        selectedQuote.setRouteMode(
                firstNonBlank(selectedQuote.getRouteMode(), recalculatedQuote.getRouteMode()));
        selectedQuote.setRentalCity(
                firstNonBlank(selectedQuote.getRentalCity(), recalculatedQuote.getRentalCity()));
        selectedQuote.setCitycode(
                firstNonBlank(selectedQuote.getCitycode(), recalculatedQuote.getCitycode()));
        selectedQuote.setAdcode(
                firstNonBlank(selectedQuote.getAdcode(), recalculatedQuote.getAdcode()));
        selectedQuote.setGroupCode(
                firstNonBlank(selectedQuote.getGroupCode(), recalculatedQuote.getGroupCode()));
        selectedQuote.setGroupName(
                firstNonBlank(selectedQuote.getGroupName(), recalculatedQuote.getGroupName()));
        selectedQuote.setDisplayName(
                firstNonBlank(selectedQuote.getDisplayName(), recalculatedQuote.getDisplayName()));
        selectedQuote.setPickupMode(
                firstNonBlank(selectedQuote.getPickupMode(), recalculatedQuote.getPickupMode()));
        selectedQuote.setReturnMode(
                firstNonBlank(selectedQuote.getReturnMode(), recalculatedQuote.getReturnMode()));
        if (selectedQuote.getPickupPoiName() == null) {
            selectedQuote.setPickupPoiName(recalculatedQuote.getPickupPoiName());
        }
        if (selectedQuote.getPickupAddress() == null) {
            selectedQuote.setPickupAddress(recalculatedQuote.getPickupAddress());
        }
        if (selectedQuote.getPickupLng() == null) {
            selectedQuote.setPickupLng(recalculatedQuote.getPickupLng());
        }
        if (selectedQuote.getPickupLat() == null) {
            selectedQuote.setPickupLat(recalculatedQuote.getPickupLat());
        }
        if (selectedQuote.getReturnPoiName() == null) {
            selectedQuote.setReturnPoiName(recalculatedQuote.getReturnPoiName());
        }
        if (selectedQuote.getReturnAddress() == null) {
            selectedQuote.setReturnAddress(recalculatedQuote.getReturnAddress());
        }
        if (selectedQuote.getReturnLng() == null) {
            selectedQuote.setReturnLng(recalculatedQuote.getReturnLng());
        }
        if (selectedQuote.getReturnLat() == null) {
            selectedQuote.setReturnLat(recalculatedQuote.getReturnLat());
        }
        if (selectedQuote.getFeeBreakdown() == null) {
            selectedQuote.setFeeBreakdown(recalculatedQuote.getFeeBreakdown());
        }
        if (selectedQuote.getPriceSnapshot() == null
                || selectedQuote.getPriceSnapshot().isEmpty()) {
            selectedQuote.setPriceSnapshot(recalculatedQuote.getPriceSnapshot());
        }
        if (selectedQuote.getAvailableCount() == null) {
            selectedQuote.setAvailableCount(recalculatedQuote.getAvailableCount());
        }
        if (selectedQuote.getDailyMileageLimitKm() == null) {
            selectedQuote.setDailyMileageLimitKm(recalculatedQuote.getDailyMileageLimitKm());
        }
        if (selectedQuote.getExtraMileageFeeCent() == null) {
            selectedQuote.setExtraMileageFeeCent(recalculatedQuote.getExtraMileageFeeCent());
        }
        if (selectedQuote.getIncludedServices() == null) {
            selectedQuote.setIncludedServices(recalculatedQuote.getIncludedServices());
        }
        return selectedQuote;
    }

    /**
     * 获取第一个非空字符串
     *
     * @param first  第一个字符串
     * @param second 第二个字符串
     * @return 第一个非空字符串，如果都为空则返回second
     */
    private String firstNonBlank(String first, String second) {
        return notBlank(first) ? first : second;
    }

    /**
     * 将Integer转换为int，null返回0
     *
     * @param value Integer值
     * @return int值
     */
    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * 判断两个对象是否相等
     *
     * @param left  左对象
     * @param right 右对象
     * @return true表示相等，false表示不相等
     */
    private boolean equalsValue(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }
}