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

@Slf4j
@Service
@RequiredArgsConstructor
public class RentalQuoteServiceImpl implements RentalQuoteService {
    private static final int DELIVERY_FEE_CENT = 3000;
    private static final int DEFAULT_QUOTE_LIMIT = 4;

    private final RentalPickupPoiMapper pickupPoiMapper;
    private final RentalOrderMapper rentalOrderMapper;
    private final RentalPriceTemplateMapper priceTemplateMapper;
    private final RentalVehicleGroupMapper vehicleGroupMapper;
    private final RentalVehicleModelMapper vehicleModelMapper;
    private final RentalStoreServiceImpl rentalStoreService;

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

    private String firstRouteCity(TravelRequirementDTO requirement) {
        return CollUtil.isEmpty(requirement.getRouteCities())
                ? null
                : requirement.getRouteCities().get(0);
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private BigDecimal decimal(String value) {
        try {
            return StrUtil.isBlank(value) ? null : new BigDecimal(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }

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

    private void appendFallbackLatestOptions(List<RentalQuoteOptionDTO> result, int safeLimit) {
        List<RentalPriceTemplate> templates =
                priceTemplateMapper.selectList(
                        new LambdaQueryWrapper<RentalPriceTemplate>()
                                .eq(RentalPriceTemplate::getStatus, 1)
                                .orderByAsc(RentalPriceTemplate::getId)
                                .last("limit " + safeLimit * 4));
        for (RentalPriceTemplate template : templates) {
            if (result.size() >= safeLimit) {
                break;
            }
            if (result.stream()
                    .anyMatch(
                            item ->
                                    Objects.equals(
                                            item.getVehicleGroupId(),
                                            template.getVehicleGroupId()))) {
                continue;
            }
            RentalVehicleGroup group = vehicleGroupMapper.selectById(template.getVehicleGroupId());
            if (group == null || !Integer.valueOf(1).equals(group.getStatus())) {
                continue;
            }
            result.add(buildTemplateQuote(template, group));
        }
    }

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
    }

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

    private String cleanRentalCity(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        return CityNameUtils.normalizeCity(value);
    }

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
            return List.of("MPV", "BUSINESS_MPV", "SUV");
        }
        if (text.contains("SUV")
                || text.contains("山路")
                || text.contains("自然")
                || text.contains("亲子")
                || text.contains("行李")) {
            return List.of("SUV", "COMFORT_SUV", "ECONOMY_SUV", "COMFORT_SEDAN");
        }
        if (text.contains("COMFORT") || text.contains("舒服") || text.contains("舒适")) {
            return List.of("COMFORT_SEDAN", "ECONOMY_SEDAN");
        }
        return List.of("ECONOMY_SEDAN", "COMFORT_SEDAN", "SUV");
    }

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

    private RentalPickupPoi choosePoi(List<RentalPickupPoi> pois, String mode) {
        if (CollUtil.isEmpty(pois)) {
            return null;
        }
        Comparator<RentalPickupPoi> comparator =
                Comparator.comparingInt((RentalPickupPoi poi) -> poiScore(poi, mode));
        return pois.stream().max(comparator).orElse(pois.get(0));
    }

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

    private List<String> cityNameVariants(String city) {
        String normalized = normalizeCity(city);
        List<String> names = new ArrayList<>();
        names.add(city);
        names.add(normalized);
        names.add(normalized + "市");
        return names.stream().distinct().toList();
    }

    private String normalizeCity(String city) {
        return CityNameUtils.normalizeCity(city) == null ? "" : CityNameUtils.normalizeCity(city);
    }

    private boolean sameCity(String left, String right) {
        return CityNameUtils.sameCity(left, right);
    }

    private LocalDate parseDate(String value) {
        try {
            return StrUtil.isBlank(value) ? LocalDate.now() : LocalDate.parse(value);
        } catch (Exception e) {
            return LocalDate.now();
        }
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

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
