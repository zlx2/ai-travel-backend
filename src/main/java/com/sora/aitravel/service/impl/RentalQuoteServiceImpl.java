package com.sora.aitravel.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.enums.RentalStoreUsageEnum;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.common.utils.CityNameUtils;
import com.sora.aitravel.dto.model.RentalArrivalPointDTO;
import com.sora.aitravel.dto.model.RentalFeeBreakdownDTO;
import com.sora.aitravel.dto.model.RentalPickupPlanDTO;
import com.sora.aitravel.dto.model.RentalQuoteOptionDTO;
import com.sora.aitravel.dto.model.RentalRequirementDTO;
import com.sora.aitravel.dto.model.RentalStoreDTO;
import com.sora.aitravel.dto.model.RentalTripContextDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.request.RentalContextPreviewRequest;
import com.sora.aitravel.dto.response.RentalContextPreviewResponse;
import com.sora.aitravel.dto.response.RentalQuotePreviewResponse;
import com.sora.aitravel.entity.RentalOrder;
import com.sora.aitravel.entity.RentalPickupPoi;
import com.sora.aitravel.entity.RentalPriceTemplate;
import com.sora.aitravel.entity.RentalVehicleGroup;
import com.sora.aitravel.entity.RentalVehicleModel;
import com.sora.aitravel.mapper.RentalOrderMapper;
import com.sora.aitravel.mapper.RentalPickupPoiMapper;
import com.sora.aitravel.mapper.RentalPriceTemplateMapper;
import com.sora.aitravel.mapper.RentalVehicleGroupMapper;
import com.sora.aitravel.mapper.RentalVehicleModelMapper;
import com.sora.aitravel.service.RentalQuoteService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 租车报价服务实现。
 *
 * <p>核心职责：
 * <ol>
 *   <li><b>上下文预览（{@link #previewContext}）</b>— 根据用户的到达信息，匹配服务点、构建行程上下文、生成报价候选；
 *   <li><b>报价预览（{@link #preview}）</b>— 根据行程需求，查找城市模板与车型，计算费用并排序返回；
 *   <li><b>报价重算（{@link #recalculate}）</b>— 用户选择某车型后，按当前参数重新精算费用；
 *   <li><b>最近订单车型（{@link #latestOrderedOptions}）</b>— 从历史订单中提取热门/最近车型，供快速展示。
 * </ol>
 *
 * <p>费用构成：租金（按日/周末差异化）+ 基础服务费 + 车辆整备费 + 异地还车费 + 送车上门费。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RentalQuoteServiceImpl implements RentalQuoteService {
    /** 送车上门费用（分），固定 30 元。 */
    private static final int DELIVERY_FEE_CENT = 3000;
    /** 报价展示数量上限。 */
    private static final int DEFAULT_QUOTE_LIMIT = 4;

    private final RentalPickupPoiMapper pickupPoiMapper;
    private final RentalOrderMapper rentalOrderMapper;
    private final RentalPriceTemplateMapper priceTemplateMapper;
    private final RentalVehicleGroupMapper vehicleGroupMapper;
    private final RentalVehicleModelMapper vehicleModelMapper;
    private final RentalStoreServiceImpl rentalStoreService;

    /**
     * 租车上下文预览——一次聚合所有前端所需的租车上下文信息。
     *
     * <p>包含：需求参数补全、到达点识别、服务点匹配、报价选项、行程上下文和取车方案。
     * 执行日志记录关键链路信息，方便排查。
     */
    @Override
    public RentalContextPreviewResponse previewContext(RentalContextPreviewRequest request) {
        long startedAt = System.currentTimeMillis();
        TravelRequirementDTO requirement = prepareContextRequirement(request);
        RentalArrivalPointDTO arrivalPoint = resolveArrivalPoint(request, requirement);
        RentalStoreDTO matchedStore =
                rentalStoreService.resolveRentalStore(
                        arrivalPoint.getName(),
                        arrivalPoint.getCityName(),
                        RentalStoreUsageEnum.PICKUP);
        List<RentalQuoteOptionDTO> quoteOptions =
                preview(requirement).getQuoteOptions().stream()
                        .map(option -> applyDynamicPickupPoint(option, matchedStore))
                        .toList();
        RentalPickupPlanDTO pickupPlan = buildPickupPlan(matchedStore);
        RentalContextPreviewResponse response =
                RentalContextPreviewResponse.builder()
                        .rentalRecommended(true)
                        .reason(buildRecommendReason(requirement))
                        .requirement(requirement)
                        .arrivalPoint(arrivalPoint)
                        .matchedStore(matchedStore)
                        .pickupPlan(pickupPlan)
                        .rentalTripContext(
                                buildRentalTripContext(
                                        requirement, arrivalPoint, matchedStore, pickupPlan))
                        .quoteOptions(quoteOptions)
                        .build();
        log.info(
                "租车上下文预览完成，destination={}, rentalCity={}, arrivalPoint={}, store={}, quoteCount={}, elapsedMs={}",
                requirement.getDestination(),
                requirement.getRentalRequirement() == null
                        ? null
                        : requirement.getRentalRequirement().getRentalStartCity(),
                arrivalPoint.getName(),
                matchedStore == null ? null : matchedStore.getDisplayName(),
                quoteOptions.size(),
                System.currentTimeMillis() - startedAt);
        return response;
    }

    /**
     * 报价预览——根据行程需求查找可用车型报价。
     *
     * <p>流程：校验需求 → 确定租车城市 → 查找城市匹配 → 查找价格模板 → 筛选并排序候选车型 →
     * 构建报价选项，最多返回 {@link #DEFAULT_QUOTE_LIMIT} 条。
     */
    @Override
    public RentalQuotePreviewResponse preview(TravelRequirementDTO requirement) {
        long startedAt = System.currentTimeMillis();
        validateRentalRequirement(requirement);
        String rentalCity = resolveRentalCity(requirement);
        CityMatch cityMatch = resolveCityMatch(rentalCity);
        List<RentalPriceTemplate> templates = findTemplates(cityMatch);
        List<RentalVehicleGroup> groups = findCandidateGroups(requirement, templates);

        List<RentalQuoteOptionDTO> options = new ArrayList<>();
        for (RentalVehicleGroup group : groups) {
            RentalPriceTemplate template =
                    templates.stream()
                            .filter(item -> Objects.equals(item.getVehicleGroupId(), group.getId()))
                            .findFirst()
                            .orElse(null);
            if (template == null) {
                continue;
            }
            options.add(buildQuote(requirement, rentalCity, cityMatch, group, template));
            if (options.size() >= DEFAULT_QUOTE_LIMIT) {
                break;
            }
        }
        if (options.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "当前城市暂无可用租车报价：" + rentalCity);
        }
        log.info(
                "租车报价预览完成，rentalCity={}, citycode={}, templateCount={}, groupCount={}, optionCount={}, elapsedMs={}",
                rentalCity,
                cityMatch.getCitycode(),
                templates.size(),
                groups.size(),
                options.size(),
                System.currentTimeMillis() - startedAt);
        return new RentalQuotePreviewResponse(
                requirement.getRouteMode(), rentalCity, cityMatch.getCitycode(), options);
    }

    /**
     * 准备上下文预览所需的完整需求参数。
     *
     * <p>补全/默认赋值逻辑：
     * <ul>
     *   <li>租车开始城市：取车城市 → 第一途经城市 → 目的地逐级降级；
     *   <li>取/还车模式：默认 DELIVERY（送车上门）；
     *   <li>租车天数：默认等于行程天数；
     *   <li>偏好列表：确保包含"租车出行"标签。
     * </ul>
     */
    private TravelRequirementDTO prepareContextRequirement(RentalContextPreviewRequest request) {
        if (request == null || request.getRequirement() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "租车上下文需求不能为空");
        }
        TravelRequirementDTO requirement = request.getRequirement();
        if (requirement.getDays() == null
                || requirement.getDays() < 1
                || requirement.getDays() > 7) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "租车天数必须在 1 到 7 天之间");
        }
        if (StrUtil.isBlank(requirement.getDestination())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "目的地不能为空");
        }

        RentalRequirementDTO rental = requirement.getRentalRequirement();
        if (rental == null) {
            rental = new RentalRequirementDTO();
            requirement.setRentalRequirement(rental);
        }
        rental.setNeedRental(true);
        String rentalStartCity =
                normalizeRentalCity(
                        firstNotBlank(
                                rental.getRentalStartCity(),
                                rental.getPickupCity(),
                                firstRouteCity(requirement),
                                requirement.getDestination()));
        rental.setRentalStartCity(rentalStartCity);
        rental.setPickupCity(
                normalizeRentalCity(firstNotBlank(rental.getPickupCity(), rentalStartCity)));
        rental.setReturnCity(
                firstNotBlank(
                        rental.getReturnCity(), rental.getRentalEndCity(), rental.getPickupCity()));
        rental.setRentalEndCity(firstNotBlank(rental.getRentalEndCity(), rental.getReturnCity()));
        rental.setPickupMode(firstNotBlank(rental.getPickupMode(), "DELIVERY"));
        rental.setReturnMode(firstNotBlank(rental.getReturnMode(), rental.getPickupMode()));
        rental.setRentalDays(
                rental.getRentalDays() == null ? requirement.getDays() : rental.getRentalDays());
        rental.setDeliveryRequired(true);
        rental.setIsOneWay(Boolean.TRUE.equals(rental.getIsOneWay()));

        requirement.setRouteMode(firstNotBlank(requirement.getRouteMode(), "LANDING_RENTAL_TRIP"));
        requirement.setTransportMode("RENTAL_CAR");
        requirement.setRentalIntent("SYSTEM_RECOMMENDED");
        requirement.setPeopleCount(
                requirement.getPeopleCount() == null ? 2 : requirement.getPeopleCount());
        requirement.setPreferences(ensureRentalPreference(requirement.getPreferences()));
        return requirement;
    }

    /**
     * 解析到达点信息。
     *
     * <p>到达点名称优先级：用户输入的到达文本 → 配送地址 → 系统从行程推断的默认到达点。
     * 城市从取车城市 → 租车开始城市 → 途经城市 → 目的地逐级降级。来源标记为 USER_PROVIDED
     * 或 SYSTEM_INFERRED，用于前端区分展示。
     */
    private RentalArrivalPointDTO resolveArrivalPoint(
            RentalContextPreviewRequest request, TravelRequirementDTO requirement) {
        RentalRequirementDTO rental = requirement.getRentalRequirement();
        String arrivalText = request.getArrivalText();
        String city =
                normalizeRentalCity(
                        firstNotBlank(
                                rental.getPickupCity(),
                                rental.getRentalStartCity(),
                                firstRouteCity(requirement),
                                requirement.getDestination()));
        String name =
                firstNotBlank(
                        arrivalText,
                        rental.getDeliveryAddress(),
                        defaultArrivalPoint(city, requirement));
        String source = StrUtil.isBlank(arrivalText) ? "SYSTEM_INFERRED" : "USER_PROVIDED";
        rental.setDeliveryAddress(name);
        return RentalArrivalPointDTO.builder().name(name).cityName(city).source(source).build();
    }

    /**
     * 将报价选项中的取/还车点替换为动态匹配到的服务点。
     *
     * <p>当用户通过上下文预览进入时，报价的取还车点应从高德实时 POI 获取，而非使用模板预设点位。
     * 同时将服务点信息快照写入 priceSnapshot 中供后续使用。
     */
    private RentalQuoteOptionDTO applyDynamicPickupPoint(
            RentalQuoteOptionDTO option, RentalStoreDTO store) {
        if (option == null || store == null) {
            return option;
        }
        option.setPickupPoiId(null);
        option.setPickupPoiName(store.getDisplayName());
        option.setPickupAddress(firstNotBlank(store.getAddress(), store.getAmapPoiName()));
        option.setPickupLng(decimal(store.getLng()));
        option.setPickupLat(decimal(store.getLat()));
        option.setReturnPoiId(null);
        option.setReturnPoiName(store.getDisplayName());
        option.setReturnAddress(firstNotBlank(store.getAddress(), store.getAmapPoiName()));
        option.setReturnLng(decimal(store.getLng()));
        option.setReturnLat(decimal(store.getLat()));
        option.setPickupMode("DYNAMIC_SERVICE_POINT");
        option.setReturnMode("SAME_SERVICE_POINT");
        Map<String, Object> snapshot =
                option.getPriceSnapshot() == null
                        ? new LinkedHashMap<>()
                        : new LinkedHashMap<>(option.getPriceSnapshot());
        snapshot.put("dynamicPickupStore", store);
        option.setPriceSnapshot(snapshot);
        return option;
    }

    /**
     * 构建取车方案展示信息。
     *
     * <p>展示送车接人的服务点名称、距离文字描述，用于前端渲染取车步骤卡片。
     */
    private RentalPickupPlanDTO buildPickupPlan(RentalStoreDTO store) {
        String servicePointName = store == null ? null : store.getDisplayName();
        Integer distanceMeters = store == null ? null : store.getDistanceMeters();
        String distanceText =
                distanceMeters == null
                        ? "附近"
                        : distanceMeters < 1000
                                ? distanceMeters + "米"
                                : String.format("%.1f公里", distanceMeters / 1000.0);
        return RentalPickupPlanDTO.builder()
                .mode("DELIVERY_PICKUP")
                .title("送车接人")
                .servicePointName(servicePointName)
                .distanceMeters(distanceMeters)
                .displayText(
                        "已匹配"
                                + (servicePointName == null ? "附近服务点" : servicePointName)
                                + "，距到达点约"
                                + distanceText
                                + "，可安排工作人员送车接人并现场交车。")
                .build();
    }

    /**
     * 构建租车行程上下文，封装到达方式、路线结构、还车点等全局信息。
     *
     * <p>返回给前端用于渲染租车行程概览区域。
     */
    private RentalTripContextDTO buildRentalTripContext(
            TravelRequirementDTO requirement,
            RentalArrivalPointDTO arrivalPoint,
            RentalStoreDTO matchedStore,
            RentalPickupPlanDTO pickupPlan) {
        return RentalTripContextDTO.builder()
                .arrivalPoint(arrivalPoint)
                .matchedStore(matchedStore)
                .pickupPlan(pickupPlan)
                .arrivalMode(arrivalMode(arrivalPoint == null ? null : arrivalPoint.getName()))
                .arrivalTimeRange("到达后取车")
                .routeStructure(
                        requirement == null || requirement.getRouteStructure() == null
                                ? "城市及周边自驾"
                                : requirement.getRouteStructure())
                .dailyDrivingLimit("近郊自驾（单日累计约2-4小时）")
                .returnMode("同城还车")
                .returnPoint(arrivalPoint == null ? null : arrivalPoint.getName())
                .build();
    }

    /**
     * 构建推荐租车的理由文本。
     *
     * <p>根据人数、天数、偏好标签生成个性化推荐文案。若无可根据理由，使用兜底文案。
     */
    private String buildRecommendReason(TravelRequirementDTO requirement) {
        List<String> reasons = new ArrayList<>();
        if (requirement.getPeopleCount() != null && requirement.getPeopleCount() >= 2) {
            reasons.add("多人同行更适合租车分摊交通成本");
        }
        if (requirement.getDays() != null && requirement.getDays() >= 2) {
            reasons.add("多日行程用车更灵活");
        }
        if (requirement.getPreferences() != null
                && requirement.getPreferences().stream()
                        .anyMatch(
                                item ->
                                        item.contains("自然")
                                                || item.contains("周边")
                                                || item.contains("亲子"))) {
            reasons.add("偏好包含周边或自然场景，自驾衔接更顺畅");
        }
        if (reasons.isEmpty()) {
            reasons.add("当前行程可通过送车接人降低到达后的交通衔接成本");
        }
        return String.join("，", reasons);
    }

    /**
     * 确保偏好列表中包含租车/自驾标签。
     *
     * <p>若用户原偏好中无相关标签，自动追加"租车出行"，供后续车型匹配使用。
     */
    private List<String> ensureRentalPreference(List<String> preferences) {
        List<String> result = new ArrayList<>();
        if (preferences != null) {
            result.addAll(preferences);
        }
        if (result.stream().noneMatch(item -> item.contains("租车") || item.contains("自驾"))) {
            result.add("租车出行");
        }
        return result;
    }

    /**
     * 根据行程的出发地和目的地文本推断默认到达点。
     *
     * <p>文本包含"机场"相关关键词 → 返回"城市+机场"；包含"高铁/火车/车站" → 返回"城市+站"；
     * 否则默认返回"城市+站"。
     */
    private String defaultArrivalPoint(String city, TravelRequirementDTO requirement) {
        String text =
                String.join(
                        " ",
                        StrUtil.nullToEmpty(requirement.getDeparture()),
                        StrUtil.nullToEmpty(requirement.getDestination()));
        if (text.contains("飞")
                || text.contains("航班")
                || text.contains("飞机")
                || text.contains("机场")) {
            return city + "机场";
        }
        if (text.contains("高铁")
                || text.contains("火车")
                || text.contains("动车")
                || text.contains("车站")) {
            return city + "站";
        }
        return city + "站";
    }

    /**
     * 根据到达点名推断到达模式。
     *
     * <p>含"机场" → 机场到达；含"站" → 高铁/火车站到达；含"酒店/宾馆/民宿" → 酒店/住宿点出发；
     * 空值 → 还不确定；其余 → 指定地址交车。
     */
    private String arrivalMode(String arrivalName) {
        if (StrUtil.isBlank(arrivalName)) {
            return "还不确定";
        }
        if (arrivalName.contains("机场")) {
            return "机场到达";
        }
        if (arrivalName.contains("站")) {
            return "高铁/火车站到达";
        }
        if (arrivalName.contains("酒店")
                || arrivalName.contains("宾馆")
                || arrivalName.contains("民宿")) {
            return "酒店/住宿点出发";
        }
        return "指定地址交车";
    }

    /** 获取行程中第一个途经城市，路由城市列表为空时返回 null。 */
    private String firstRouteCity(TravelRequirementDTO requirement) {
        return CollUtil.isEmpty(requirement.getRouteCities())
                ? null
                : requirement.getRouteCities().get(0);
    }

    /** 返回第一个非空白字符串的便捷方法。 */
    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    /** 将字符串解析为 BigDecimal，不可解析时返回 null。 */
    private BigDecimal decimal(String value) {
        try {
            return StrUtil.isBlank(value) ? null : new BigDecimal(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /**
     * 报价重算——用户选择某车型后，按最新参数重新精确计算费用。
     *
     * <p>确保所选车型在当前城市仍可用且启售，然后重新调用 buildQuote 生成完整报价。
     */
    @Override
    public RentalQuoteOptionDTO recalculate(
            TravelRequirementDTO requirement, RentalQuoteOptionDTO selectedQuote) {
        long startedAt = System.currentTimeMillis();
        if (selectedQuote == null || selectedQuote.getVehicleGroupId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择租车报价");
        }
        String rentalCity = resolveRentalCity(requirement);
        CityMatch cityMatch = resolveCityMatch(rentalCity);
        RentalPriceTemplate template =
                findTemplates(cityMatch).stream()
                        .filter(
                                item ->
                                        Objects.equals(
                                                item.getVehicleGroupId(),
                                                selectedQuote.getVehicleGroupId()))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.NOT_FOUND, "所选车型在当前城市暂无可用报价"));
        RentalVehicleGroup group = vehicleGroupMapper.selectById(selectedQuote.getVehicleGroupId());
        if (group == null || !Integer.valueOf(1).equals(group.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "所选车型不可用");
        }
        RentalQuoteOptionDTO quote =
                buildQuote(requirement, rentalCity, cityMatch, group, template);
        log.info(
                "租车报价重算完成，rentalCity={}, selectedQuote={}, vehicleGroupId={}, totalPriceCent={}, elapsedMs={}",
                rentalCity,
                selectedQuote.getQuoteId(),
                selectedQuote.getVehicleGroupId(),
                quote.getFeeBreakdown() == null
                        ? null
                        : quote.getFeeBreakdown().getTotalPriceCent(),
                System.currentTimeMillis() - startedAt);
        return quote;
    }

    /**
     * 获取最近订单中使用过的车型报价。
     *
     * <p>优先从已确认的订单中提取，若无则退而取最新订单。按车型去重，数量不足时用模板降级补充。
     * 用于首页/快捷入口快速展示热门车型。
     */
    @Override
    public List<RentalQuoteOptionDTO> latestOrderedOptions(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 8));
        List<RentalOrder> orders =
                rentalOrderMapper.selectList(
                        new LambdaQueryWrapper<RentalOrder>()
                                .eq(RentalOrder::getOrderStatus, "confirmed")
                                .orderByDesc(RentalOrder::getCreateTime)
                                .last("limit " + safeLimit * 3));
        if (orders.isEmpty()) {
            orders =
                    rentalOrderMapper.selectList(
                            new LambdaQueryWrapper<RentalOrder>()
                                    .orderByDesc(RentalOrder::getCreateTime)
                                    .last("limit " + safeLimit * 3));
        }
        List<RentalQuoteOptionDTO> result = new ArrayList<>();
        for (RentalOrder order : orders) {
            if (result.stream()
                    .anyMatch(
                            item ->
                                    Objects.equals(
                                            item.getVehicleGroupId(), order.getVehicleGroupId()))) {
                continue;
            }
            RentalVehicleGroup group = vehicleGroupMapper.selectById(order.getVehicleGroupId());
            if (group == null || !Integer.valueOf(1).equals(group.getStatus())) {
                continue;
            }
            RentalPriceTemplate template =
                    order.getPriceTemplateId() == null
                            ? null
                            : priceTemplateMapper.selectById(order.getPriceTemplateId());
            result.add(buildOrderQuote(order, group, template));
            if (result.size() >= safeLimit) {
                break;
            }
        }
        if (result.size() < safeLimit) {
            appendFallbackLatestOptions(result, safeLimit);
        }
        return result;
    }

    /**
     * 在最近订单车型不足时，从启用的价格模板中补充候选车型。
     *
     * <p>按模板 ID 升序依次添加，直到达到 safeLimit 或取完可用模板。
     */
    private void appendFallbackLatestOptions(List<RentalQuoteOptionDTO> result, int safeLimit) {
        List<RentalPriceTemplate> templates =
                priceTemplateMapper.selectList(
                        new LambdaQueryWrapper<RentalPriceTemplate>()
                                .eq(RentalPriceTemplate::getStatus, 1)
                                .orderByAsc(RentalPriceTemplate::getId)
                                .last("limit " + safeLimit * 8));
        for (RentalPriceTemplate template : templates) {
            if (result.size() >= safeLimit) {
                break;
            }
            if (result.stream()
                    .anyMatch(
                            item -> Objects.equals(item.getPriceTemplateId(), template.getId()))) {
                continue;
            }
            RentalVehicleGroup group = vehicleGroupMapper.selectById(template.getVehicleGroupId());
            if (group == null || !Integer.valueOf(1).equals(group.getStatus())) {
                continue;
            }
            result.add(buildTemplateQuote(template, group));
        }
    }

    /**
     * 校验租车报价需求的合法性与完备性。
     *
     * <p>检查点：是否确实需要租车（通过 routeMode / transportMode / rentalIntent 综合判断）、
     * 天数（1~7）、人数（1~7）。任一条件不满足即抛参数异常。
     */
    private void validateRentalRequirement(TravelRequirementDTO requirement) {
        if (requirement == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "租车报价需求不能为空");
        }
        RentalRequirementDTO rental = requirement.getRentalRequirement();
        boolean needRental =
                rental != null && Boolean.TRUE.equals(rental.getNeedRental())
                        || "ROAD_TRIP".equals(requirement.getRouteMode())
                        || "LANDING_RENTAL_TRIP".equals(requirement.getRouteMode())
                        || "RENTAL_CAR".equals(requirement.getTransportMode())
                        || "SELF_DRIVE".equals(requirement.getTransportMode())
                        || "USER_REQUIRED".equals(requirement.getRentalIntent());
        if (!needRental) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "当前需求未选择租车");
        }
        if (requirement.getDays() == null
                || requirement.getDays() < 1
                || requirement.getDays() > 7) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "租车天数必须在 1 到 7 天之间");
        }
        if (requirement.getPeopleCount() == null
                || requirement.getPeopleCount() < 1
                || requirement.getPeopleCount() > 7) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "乘车人数必须在 1 到 7 人之间");
        }
    }

    /**
     * 从行程需求中确定租车城市。
     *
     * <p>优先级：租车开始城市 → 取车城市 → 公路旅行取出发地 → 途经城市首城 → 目的地。
     * 最终经过 {@link #normalizeRentalCity} 标准化。
     */
    private String resolveRentalCity(TravelRequirementDTO requirement) {
        RentalRequirementDTO rental = requirement.getRentalRequirement();
        if (rental != null && StrUtil.isNotBlank(rental.getRentalStartCity())) {
            return normalizeRentalCity(rental.getRentalStartCity());
        }
        if (rental != null && StrUtil.isNotBlank(rental.getPickupCity())) {
            return normalizeRentalCity(rental.getPickupCity());
        }
        if ("ROAD_TRIP".equals(requirement.getRouteMode())) {
            return normalizeRentalCity(requirement.getDeparture());
        }
        if (CollUtil.isNotEmpty(requirement.getRouteCities())) {
            return normalizeRentalCity(requirement.getRouteCities().get(0));
        }
        return normalizeRentalCity(requirement.getDestination());
    }

    /**
     * 标准化城市名称。
     *
     * <p>处理多城市连写（如"上海杭州"或"上海、杭州"），取第一个可识别的城市名。
     * 最终通过 {@link CityNameUtils#normalizeCity} 归一化。
     */
    private String normalizeRentalCity(String value) {
        if (StrUtil.isBlank(value)) {
            return value;
        }
        String[] parts = value.trim().split("[、,，/|；;\\s]+|和|及|与|到|至|\\+");
        for (String part : parts) {
            String city = cleanRentalCity(part);
            if (StrUtil.isNotBlank(city)) {
                return city;
            }
        }
        return cleanRentalCity(value);
    }

    /** 用 CityNameUtils 标准化城市名，空白值返回 null。 */
    private String cleanRentalCity(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        return CityNameUtils.normalizeCity(value);
    }

    /**
     * 解析租车城市与系统数据的匹配关系。
     *
     * <p>先尝试通过取还车 POI 表匹配城市（含城市名变体），若命中则带回城市编码和 POI 列表；
     * 若无 POI，再尝试通过价格模板表匹配城市，仅带回城市编码；都失败则抛异常。
     */
    private CityMatch resolveCityMatch(String rentalCity) {
        if (StrUtil.isBlank(rentalCity)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "租车城市不能为空");
        }
        List<String> names = cityNameVariants(rentalCity);
        List<RentalPickupPoi> pois =
                pickupPoiMapper.selectList(
                        new LambdaQueryWrapper<RentalPickupPoi>()
                                .in(RentalPickupPoi::getCity, names)
                                .eq(RentalPickupPoi::getStatus, 1)
                                .last("limit 20"));
        if (!pois.isEmpty()) {
            RentalPickupPoi first = pois.get(0);
            return new CityMatch(first.getCity(), first.getCitycode(), first.getAdcode(), pois);
        }
        List<RentalPriceTemplate> templates =
                priceTemplateMapper.selectList(
                        new LambdaQueryWrapper<RentalPriceTemplate>()
                                .in(RentalPriceTemplate::getCity, names)
                                .eq(RentalPriceTemplate::getStatus, 1)
                                .last("limit 20"));
        if (!templates.isEmpty()) {
            RentalPriceTemplate first = templates.get(0);
            return new CityMatch(
                    first.getCity(), first.getCitycode(), first.getAdcode(), List.of());
        }
        throw new BusinessException(ErrorCode.NOT_FOUND, "当前城市暂无租车数据：" + rentalCity);
    }

    /**
     * 根据城市匹配查找已启用的价格模板。
     *
     * <p>优先按城市编码（citycode）查询，编码为空时回退到城市名查询。
     */
    private List<RentalPriceTemplate> findTemplates(CityMatch cityMatch) {
        LambdaQueryWrapper<RentalPriceTemplate> query =
                new LambdaQueryWrapper<RentalPriceTemplate>().eq(RentalPriceTemplate::getStatus, 1);
        if (StrUtil.isNotBlank(cityMatch.getCitycode())) {
            query.eq(RentalPriceTemplate::getCitycode, cityMatch.getCitycode());
        } else {
            query.eq(RentalPriceTemplate::getCity, cityMatch.getCity());
        }
        List<RentalPriceTemplate> templates = priceTemplateMapper.selectList(query);
        if (templates.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "当前城市暂无租车价格模板：" + cityMatch.getCity());
        }
        return templates;
    }

    /**
     * 从价格模板对应的车型中筛选并排序候选车型。
     *
     * <p>筛选条件：车型启用、座位数满足人数要求。排序规则：按偏好打分降序 → sortOrder 升序。
     * 若无满足座位要求的车型则抛异常。
     */
    private List<RentalVehicleGroup> findCandidateGroups(
            TravelRequirementDTO requirement, List<RentalPriceTemplate> templates) {
        List<Long> groupIds =
                templates.stream().map(RentalPriceTemplate::getVehicleGroupId).toList();
        List<RentalVehicleGroup> groups =
                vehicleGroupMapper.selectList(
                        new LambdaQueryWrapper<RentalVehicleGroup>()
                                .in(RentalVehicleGroup::getId, groupIds)
                                .eq(RentalVehicleGroup::getStatus, 1));
        if (groups.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "暂无可用车型");
        }
        int people = requirement.getPeopleCount() == null ? 1 : requirement.getPeopleCount();
        groups =
                new ArrayList<>(
                        groups.stream()
                                .filter(
                                        group ->
                                                group.getSeatsMax() != null
                                                        && group.getSeatsMax() >= people)
                                .toList());
        if (groups.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "暂无满足 " + people + " 人出行的可用车型");
        }
        List<String> preferredCodes = preferredGroupCodes(requirement);
        groups.sort(
                Comparator.comparingInt(
                                (RentalVehicleGroup group) ->
                                        scoreGroup(group, preferredCodes, requirement))
                        .reversed()
                        .thenComparing(
                                RentalVehicleGroup::getSortOrder,
                                Comparator.nullsLast(Integer::compareTo)));
        return groups;
    }

    /**
     * 根据用户偏好和人数确定偏好的车型编码列表。
     *
     * <p>优先级规则（按匹配顺序）：
     * <ol>
     *   <li>新能源偏好 → EV 轿车；
     *   <li>豪华/商务偏好 → 豪华/商务轿车；
     *   <li>≥5 人或 MPV 偏好 → MPV / SUV；
     *   <li>SUV / 山路/自然/亲子偏好 → SUV 系；
     *   <li>舒适偏好 → 舒适轿车；
     *   <li>默认 → 经济轿车/舒适轿车/中型轿车/紧凑 SUV。
     * </ol>
     */
    private List<String> preferredGroupCodes(TravelRequirementDTO requirement) {
        int people = requirement.getPeopleCount() == null ? 2 : requirement.getPeopleCount();
        String vehiclePreference =
                requirement.getRentalRequirement() == null
                        ? ""
                        : StrUtil.nullToEmpty(
                                requirement.getRentalRequirement().getVehiclePreference());
        List<String> preferences =
                requirement.getPreferences() == null ? List.of() : requirement.getPreferences();
        String text =
                (vehiclePreference + " " + String.join(" ", preferences)).toUpperCase(Locale.ROOT);
        if (text.contains("EV") || text.contains("新能源") || text.contains("电")) {
            return List.of("EV_SEDAN", "NEW_ENERGY_SEDAN", "ELECTRIC_SEDAN", "COMFORT_SEDAN");
        }
        if (text.contains("LUXURY")
                || text.contains("豪华")
                || text.contains("商务")
                || text.contains("高端")) {
            return List.of("LUXURY_SEDAN", "BUSINESS_SEDAN", "COMFORT_SEDAN");
        }
        if (people >= 5 || text.contains("MPV")) {
            return List.of("MPV_7_SEAT", "MPV", "BUSINESS_MPV", "COMPACT_SUV", "SUV");
        }
        if (text.contains("SUV")
                || text.contains("山路")
                || text.contains("自然")
                || text.contains("亲子")
                || text.contains("行李")) {
            return List.of("COMPACT_SUV", "SUV", "COMFORT_SUV", "ECONOMY_SUV", "COMFORT_SEDAN");
        }
        if (text.contains("COMFORT") || text.contains("舒服") || text.contains("舒适")) {
            return List.of("COMFORT_SEDAN", "ECONOMY_SEDAN");
        }
        return List.of("ECONOMY_SEDAN", "COMFORT_SEDAN", "MID_SIZE_SEDAN", "COMPACT_SUV");
    }

    /**
     * 给车型打分，分值越高越优先推荐。
     *
     * <p>完全匹配偏好编码列表中位置靠前得高分（100 ~ 55），不在列表中得基础分 20。
     * 预算充足且偏好舒适型时额外 +10。
     */
    private int scoreGroup(
            RentalVehicleGroup group,
            List<String> preferredCodes,
            TravelRequirementDTO requirement) {
        String code = StrUtil.nullToEmpty(group.getGroupCode()).toUpperCase(Locale.ROOT);
        int index = preferredCodes.indexOf(code);
        int score = index >= 0 ? 100 - index * 15 : 20;
        if (requirement.getBudget() != null
                && requirement.getBudget() >= 8000
                && code.contains("COMFORT")) {
            score += 10;
        }
        return score;
    }

    /**
     * 构建完整的报价选项。
     *
     * <p>聚合价格模板、车型分组、取还车 POI、费用明细等信息，生成一个完整的 {@link RentalQuoteOptionDTO}。
     * 同时生成 priceSnapshot 快照，记录所有用于计算报价的原始参数。
     */
    private RentalQuoteOptionDTO buildQuote(
            TravelRequirementDTO requirement,
            String rentalCity,
            CityMatch cityMatch,
            RentalVehicleGroup group,
            RentalPriceTemplate template) {
        RentalRequirementDTO rental = requirement.getRentalRequirement();
        String pickupMode =
                rental == null
                        ? "UNKNOWN"
                        : StrUtil.blankToDefault(rental.getPickupMode(), "UNKNOWN");
        String returnMode =
                rental == null
                        ? pickupMode
                        : StrUtil.blankToDefault(rental.getReturnMode(), pickupMode);
        RentalPickupPoi pickupPoi = choosePoi(cityMatch.getPois(), pickupMode);
        int rentalDays =
                rental != null && rental.getRentalDays() != null
                        ? rental.getRentalDays()
                        : requirement.getDays();
        boolean isOneWay = rental != null && Boolean.TRUE.equals(rental.getIsOneWay());
        boolean delivery = rental != null && Boolean.TRUE.equals(rental.getDeliveryRequired());
        CityMatch returnCityMatch = resolveReturnCityMatch(requirement, cityMatch, isOneWay);
        RentalPickupPoi returnPoi = choosePoi(returnCityMatch.getPois(), returnMode);

        RentalFeeBreakdownDTO fee =
                calculateFee(requirement, template, rentalDays, isOneWay, delivery);
        RentalVehicleModel model = chooseRepresentativeModel(group);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("source", "rental_price_template");
        snapshot.put("templateId", template.getId());
        snapshot.put("city", rentalCity);
        snapshot.put("citycode", cityMatch.getCitycode());
        snapshot.put("returnCity", returnCityMatch.getCity());
        snapshot.put("returnCitycode", returnCityMatch.getCitycode());
        snapshot.put("vehicleGroupId", group.getId());
        snapshot.put("groupCode", group.getGroupCode());
        snapshot.put("vehicleModelId", model == null ? null : model.getId());
        snapshot.put("vehicleModelName", modelName(model, group));
        snapshot.put("rentalDays", rentalDays);
        snapshot.put("dailyMileageLimitKm", template.getDailyMileageLimitKm());
        snapshot.put("extraMileageFeeCent", template.getExtraMileageFeeCent());
        snapshot.put("includedServices", template.getIncludedServices());
        snapshot.put("feeBreakdown", fee);

        RentalQuoteOptionDTO.RentalQuoteOptionDTOBuilder builder =
                RentalQuoteOptionDTO.builder()
                        .quoteId("Q-" + template.getId() + "-" + group.getId())
                        .routeMode(requirement.getRouteMode())
                        .rentalCity(rentalCity)
                        .citycode(cityMatch.getCitycode())
                        .adcode(cityMatch.getAdcode());
        applyGroupAndModelFields(builder, group, model);
        return builder.pickupPoiId(pickupPoi == null ? null : pickupPoi.getId())
                .pickupPoiName(pickupPoi == null ? null : pickupPoi.getPoiName())
                .pickupAddress(pickupPoi == null ? null : pickupPoi.getAddress())
                .pickupLng(pickupPoi == null ? null : pickupPoi.getLongitude())
                .pickupLat(pickupPoi == null ? null : pickupPoi.getLatitude())
                .returnPoiId(returnPoi == null ? null : returnPoi.getId())
                .returnPoiName(returnPoi == null ? null : returnPoi.getPoiName())
                .returnAddress(returnPoi == null ? null : returnPoi.getAddress())
                .returnLng(returnPoi == null ? null : returnPoi.getLongitude())
                .returnLat(returnPoi == null ? null : returnPoi.getLatitude())
                .pickupMode(pickupMode)
                .returnMode(returnMode)
                .rentalDays(rentalDays)
                .isOneWay(isOneWay)
                .priceTemplateId(template.getId())
                .availableCount(template.getAvailableCount())
                .dailyMileageLimitKm(template.getDailyMileageLimitKm())
                .extraMileageFeeCent(template.getExtraMileageFeeCent())
                .includedServices(template.getIncludedServices())
                .feeBreakdown(fee)
                .priceSnapshot(snapshot)
                .build();
    }

    /**
     * 解析异地还车城市匹配。
     *
     * <p>仅当启用了异地还车（isOneWay）且还车城市与取车城市不同时，才单独查询还车城市匹配。
     * 同城还车直接复用取车城市的匹配结果。
     */
    private CityMatch resolveReturnCityMatch(
            TravelRequirementDTO requirement, CityMatch pickupCityMatch, boolean isOneWay) {
        if (!isOneWay || requirement.getRentalRequirement() == null) {
            return pickupCityMatch;
        }
        RentalRequirementDTO rental = requirement.getRentalRequirement();
        String returnCity =
                StrUtil.isNotBlank(rental.getRentalEndCity())
                        ? rental.getRentalEndCity()
                        : rental.getReturnCity();
        if (StrUtil.isBlank(returnCity) || sameCity(returnCity, pickupCityMatch.getCity())) {
            return pickupCityMatch;
        }
        return resolveCityMatch(returnCity);
    }

    /**
     * 从车型分组中选择一个代表车型用于展示。
     *
     * <p>优先选取有图片的车型（有图优先），取第一个可用车型作为该组视觉展示的代表。
     */
    private RentalVehicleModel chooseRepresentativeModel(RentalVehicleGroup group) {
        if (group == null) {
            return null;
        }
        LambdaQueryWrapper<RentalVehicleModel> query =
                new LambdaQueryWrapper<RentalVehicleModel>()
                        .eq(RentalVehicleModel::getStatus, 1)
                        .orderByDesc(RentalVehicleModel::getImageUrl)
                        .orderByAsc(RentalVehicleModel::getId)
                        .last("limit 1");
        if (group.getId() != null) {
            query.and(
                    wrapper ->
                            wrapper.eq(RentalVehicleModel::getGroupId, group.getId())
                                    .or()
                                    .eq(RentalVehicleModel::getGroupCode, group.getGroupCode()));
        } else {
            query.eq(RentalVehicleModel::getGroupCode, group.getGroupCode());
        }
        return vehicleModelMapper.selectOne(query);
    }

    /**
     * 根据历史订单构建报价选项。
     *
     * <p>订单中已包含实际成交的费用明细，直接透传；模板信息若存在则附加，不存在时相关字段置空。
     */
    private RentalQuoteOptionDTO buildOrderQuote(
            RentalOrder order, RentalVehicleGroup group, RentalPriceTemplate template) {
        RentalVehicleModel model = chooseRepresentativeModel(group);
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
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("source", "rental_order");
        snapshot.put("orderId", order.getId());
        snapshot.put("vehicleGroupId", group.getId());
        snapshot.put("groupCode", group.getGroupCode());
        snapshot.put("vehicleModelId", model == null ? null : model.getId());
        snapshot.put("vehicleModelName", modelName(model, group));
        snapshot.put(
                "rentalDays",
                order.getRentalDays() == null ? null : order.getRentalDays().intValue());
        snapshot.put("feeBreakdown", fee);
        RentalQuoteOptionDTO.RentalQuoteOptionDTOBuilder builder =
                RentalQuoteOptionDTO.builder().quoteId("O-" + order.getId() + "-" + group.getId());
        applyGroupAndModelFields(builder, group, model);
        return builder.pickupPoiId(order.getPickupPoiId())
                .returnPoiId(order.getReturnPoiId())
                .pickupMode(order.getPickupMode())
                .returnMode(order.getReturnMode())
                .rentalDays(order.getRentalDays() == null ? null : order.getRentalDays().intValue())
                .isOneWay(Integer.valueOf(1).equals(order.getIsOneWay()))
                .priceTemplateId(order.getPriceTemplateId())
                .availableCount(template == null ? null : template.getAvailableCount())
                .dailyMileageLimitKm(template == null ? null : template.getDailyMileageLimitKm())
                .extraMileageFeeCent(template == null ? null : template.getExtraMileageFeeCent())
                .includedServices(template == null ? null : template.getIncludedServices())
                .feeBreakdown(fee)
                .priceSnapshot(snapshot)
                .build();
    }

    /**
     * 根据价格模板构建报价选项（降级方案）。
     *
     * <p>当没有历史订单可用时，以模板为基准按 2 天租期估算费用并生成报价。
     * 不涉及异地还车和送车上门费用，取还车模式标记为 CITY。
     */
    private RentalQuoteOptionDTO buildTemplateQuote(
            RentalPriceTemplate template, RentalVehicleGroup group) {
        RentalVehicleModel model = chooseRepresentativeModel(group);
        int rentalDays = 2;
        int rentalFee = value(template.getWeekdayRentalFeeCent()) * rentalDays;
        int baseServiceFee = value(template.getBaseServiceFeeCent()) * rentalDays;
        int prepareFee = value(template.getVehiclePrepareFeeCent());
        RentalFeeBreakdownDTO fee =
                RentalFeeBreakdownDTO.builder()
                        .rentalFeeCent(rentalFee)
                        .baseServiceFeeCent(baseServiceFee)
                        .vehiclePrepareFeeCent(prepareFee)
                        .oneWayFeeCent(0)
                        .deliveryFeeCent(0)
                        .totalPriceCent(rentalFee + baseServiceFee + prepareFee)
                        .rentalDepositCent(value(template.getRentalDepositCent()))
                        .violationDepositCent(value(template.getViolationDepositCent()))
                        .depositFreeThresholdScore(template.getDepositFreeThresholdScore())
                        .build();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("source", "rental_price_template_fallback");
        snapshot.put("templateId", template.getId());
        snapshot.put("city", template.getCity());
        snapshot.put("citycode", template.getCitycode());
        snapshot.put("vehicleGroupId", group.getId());
        snapshot.put("groupCode", group.getGroupCode());
        snapshot.put("vehicleModelId", model == null ? null : model.getId());
        snapshot.put("vehicleModelName", modelName(model, group));
        snapshot.put("rentalDays", rentalDays);
        snapshot.put("feeBreakdown", fee);

        RentalQuoteOptionDTO.RentalQuoteOptionDTOBuilder builder =
                RentalQuoteOptionDTO.builder()
                        .quoteId("T-" + template.getId() + "-" + group.getId());
        applyGroupAndModelFields(builder, group, model);
        return builder.rentalCity(template.getCity())
                .citycode(template.getCitycode())
                .adcode(template.getAdcode())
                .pickupMode("CITY")
                .returnMode("CITY")
                .rentalDays(rentalDays)
                .isOneWay(false)
                .priceTemplateId(template.getId())
                .availableCount(template.getAvailableCount())
                .dailyMileageLimitKm(template.getDailyMileageLimitKm())
                .extraMileageFeeCent(template.getExtraMileageFeeCent())
                .includedServices(template.getIncludedServices())
                .feeBreakdown(fee)
                .priceSnapshot(snapshot)
                .build();
    }

    /** 获取车型名称：优先车系全名，退而求"品牌 系列"拼接，模型为 null 时用分组名称。 */
    private String modelName(RentalVehicleModel model, RentalVehicleGroup group) {
        if (model == null) {
            return group == null
                    ? null
                    : StrUtil.blankToDefault(group.getDisplayName(), group.getGroupName());
        }
        if (StrUtil.isNotBlank(model.getSeriesFullName())) {
            return model.getSeriesFullName();
        }
        return (StrUtil.nullToEmpty(model.getBrand())
                        + " "
                        + StrUtil.nullToEmpty(model.getSeries()))
                .trim();
    }

    /**
     * 计算完整费用明细。
     *
     * <p>费用组成：
     * <ul>
     *   <li>租金 = 逐日按工作日/周末费率累加；
     *   <li>基础服务费 = 日费率 × 天数；
     *   <li>整备费 = 一次性费用；
     *   <li>异地还车费 = 基础异地费 × 折扣率（费率折扣可能为 1.0 即无折扣）；
     *   <li>送车上门费 = 固定 30 元；
     *   <li>总计 = 以上各项之和。
     * </ul>
     */
    private RentalFeeBreakdownDTO calculateFee(
            TravelRequirementDTO requirement,
            RentalPriceTemplate template,
            int rentalDays,
            boolean isOneWay,
            boolean delivery) {
        int rentalFee = 0;
        LocalDate start = parseDate(requirement.getTravelDate());
        for (int i = 0; i < rentalDays; i++) {
            LocalDate day = start.plusDays(i);
            boolean weekend =
                    day.getDayOfWeek() == DayOfWeek.SATURDAY
                            || day.getDayOfWeek() == DayOfWeek.SUNDAY;
            rentalFee +=
                    value(
                            weekend
                                    ? template.getWeekendRentalFeeCent()
                                    : template.getWeekdayRentalFeeCent());
        }
        int baseServiceFee = value(template.getBaseServiceFeeCent()) * rentalDays;
        int prepareFee = value(template.getVehiclePrepareFeeCent());
        int oneWayFee = 0;
        if (isOneWay) {
            BigDecimal discount =
                    template.getOneWayDiscountRate() == null
                            ? BigDecimal.ONE
                            : template.getOneWayDiscountRate();
            oneWayFee =
                    BigDecimal.valueOf(value(template.getOneWayBaseFeeCent()))
                            .multiply(discount)
                            .setScale(0, RoundingMode.HALF_UP)
                            .intValue();
        }
        int deliveryFee = delivery ? DELIVERY_FEE_CENT : 0;
        int total = rentalFee + baseServiceFee + prepareFee + oneWayFee + deliveryFee;
        return new RentalFeeBreakdownDTO(
                rentalFee,
                baseServiceFee,
                prepareFee,
                oneWayFee,
                deliveryFee,
                total,
                value(template.getRentalDepositCent()),
                value(template.getViolationDepositCent()),
                template.getDepositFreeThresholdScore());
    }

    /**
     * 根据取车模式从 POI 列表中选出最合适的取/还车点。
     *
     * <p>按 {@link #poiScore} 打分，分数最高者胜出。列表为空时返回 null。
     */
    private RentalPickupPoi choosePoi(List<RentalPickupPoi> pois, String mode) {
        if (CollUtil.isEmpty(pois)) {
            return null;
        }
        Comparator<RentalPickupPoi> comparator =
                Comparator.comparingInt((RentalPickupPoi poi) -> poiScore(poi, mode));
        return pois.stream().max(comparator).orElse(pois.get(0));
    }

    /**
     * 给取还车 POI 打分。
     *
     * <p>机场模式 → 匹配"机场"名称或 150104 类型码得 100 分；火车站模式 → 匹配"高铁/火车/车站"
     * 名称或 150200 类型码得 100 分；其他模式 → 名称含"市区/中心"得 60 分，否则 10 分。
     */
    private int poiScore(RentalPickupPoi poi, String mode) {
        String name = StrUtil.nullToEmpty(poi.getPoiName());
        String type =
                StrUtil.nullToEmpty(poi.getPoiType()) + StrUtil.nullToEmpty(poi.getPoiTypecode());
        if ("AIRPORT".equals(mode)) {
            return StrUtil.containsAny(name + type, "机场", "150104") ? 100 : 1;
        }
        if ("TRAIN_STATION".equals(mode)) {
            return StrUtil.containsAny(name + type, "高铁", "火车", "车站", "150200") ? 100 : 1;
        }
        return StrUtil.containsAny(name, "市区", "中心") ? 60 : 10;
    }

    /** 生成城市名称的变体列表（原名、标准名、标准名+市），用于数据库模糊匹配。 */
    private List<String> cityNameVariants(String city) {
        String normalized = normalizeCity(city);
        List<String> names = new ArrayList<>();
        names.add(city);
        names.add(normalized);
        names.add(normalized + "市");
        return names.stream().distinct().toList();
    }

    /** 用 CityNameUtils 标准化城市名，null 时返回空字符串。 */
    private String normalizeCity(String city) {
        return CityNameUtils.normalizeCity(city) == null ? "" : CityNameUtils.normalizeCity(city);
    }

    /** 判断两个城市名是否代表同一城市（委托 CityNameUtils）。 */
    private boolean sameCity(String left, String right) {
        return CityNameUtils.sameCity(left, right);
    }

    /** 安全解析日期字符串，不可解析时返回当天。 */
    private LocalDate parseDate(String value) {
        try {
            return StrUtil.isBlank(value) ? LocalDate.now() : LocalDate.parse(value);
        } catch (Exception e) {
            return LocalDate.now();
        }
    }

    /** Integer 空安全取值，null 时返回 0。 */
    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * 将车型分组和代表车型的字段批量写入 Builder。
     *
     * <p>模型非空时优先使用模型字段（能源类型、座位数、车身形式、变速箱等），
     * 模型为空时降级使用分组字段。减少调用方重复的判空和字段拷贝。
     */
    private void applyGroupAndModelFields(
            RentalQuoteOptionDTO.RentalQuoteOptionDTOBuilder builder,
            RentalVehicleGroup group,
            RentalVehicleModel model) {
        builder.vehicleGroupId(group.getId())
                .groupCode(group.getGroupCode())
                .groupName(group.getGroupName())
                .displayName(group.getDisplayName())
                .vehicleClass(group.getVehicleClass())
                .energyType(model == null ? group.getEnergyType() : model.getEnergyType())
                .seatsMin(group.getSeatsMin())
                .seatsMax(group.getSeatsMax())
                .recommendedPeople(group.getRecommendedPeople())
                .recommendedLuggage(group.getRecommendedLuggage())
                .travelTags(group.getTravelTags())
                .exampleModels(group.getExampleModels())
                .description(group.getDescription())
                .iconUrl(group.getIconUrl())
                .vehicleModelId(model == null ? null : model.getId())
                .brand(model == null ? null : model.getBrand())
                .series(model == null ? null : model.getSeries())
                .seriesFullName(modelName(model, group))
                .modelYear(model == null ? null : model.getModelYear())
                .bodyType(model == null ? group.getBodyType() : model.getBodyType())
                .transmission(model == null ? group.getTransmission() : model.getTransmission())
                .seats(model == null ? group.getSeatsMax() : model.getSeats())
                .imageUrl(model == null ? null : model.getImageUrl())
                .summary(model == null ? group.getDescription() : model.getSummary())
                .featureTags(model == null ? group.getTravelTags() : model.getFeatureTags());
    }

    /** 城市匹配结果内部类：城市名、城市编码、区域编码、POI 列表。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private class CityMatch {

        private String city;
        private String citycode;
        private String adcode;
        private List<RentalPickupPoi> pois;
    }
}
