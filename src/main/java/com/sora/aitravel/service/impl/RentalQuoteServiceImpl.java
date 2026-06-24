package com.sora.aitravel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.dto.model.RentalFeeBreakdownDTO;
import com.sora.aitravel.dto.model.RentalQuoteOptionDTO;
import com.sora.aitravel.dto.model.RentalRequirementDTO;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.response.RentalQuotePreviewResponse;
import com.sora.aitravel.entity.RentalPickupPoi;
import com.sora.aitravel.entity.RentalPriceTemplate;
import com.sora.aitravel.entity.RentalVehicleGroup;
import com.sora.aitravel.mapper.RentalPickupPoiMapper;
import com.sora.aitravel.mapper.RentalPriceTemplateMapper;
import com.sora.aitravel.mapper.RentalVehicleGroupMapper;
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
import org.springframework.stereotype.Service;

@Service
public class RentalQuoteServiceImpl implements RentalQuoteService {
    private static final int DELIVERY_FEE_CENT = 3000;

    private final RentalPickupPoiMapper pickupPoiMapper;
    private final RentalPriceTemplateMapper priceTemplateMapper;
    private final RentalVehicleGroupMapper vehicleGroupMapper;

    public RentalQuoteServiceImpl(
            RentalPickupPoiMapper pickupPoiMapper,
            RentalPriceTemplateMapper priceTemplateMapper,
            RentalVehicleGroupMapper vehicleGroupMapper) {
        this.pickupPoiMapper = pickupPoiMapper;
        this.priceTemplateMapper = priceTemplateMapper;
        this.vehicleGroupMapper = vehicleGroupMapper;
    }

    @Override
    public RentalQuotePreviewResponse preview(TravelRequirementDTO requirement) {
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
            if (options.size() >= 3) {
                break;
            }
        }
        if (options.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "当前城市暂无可用租车报价：" + rentalCity);
        }
        return new RentalQuotePreviewResponse(
                requirement.routeMode(), rentalCity, cityMatch.citycode(), options);
    }

    @Override
    public RentalQuoteOptionDTO recalculate(
            TravelRequirementDTO requirement, RentalQuoteOptionDTO selectedQuote) {
        if (selectedQuote == null || selectedQuote.vehicleGroupId() == null) {
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
                                                selectedQuote.vehicleGroupId()))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.NOT_FOUND, "所选车型在当前城市暂无可用报价"));
        RentalVehicleGroup group = vehicleGroupMapper.selectById(selectedQuote.vehicleGroupId());
        if (group == null || !Integer.valueOf(1).equals(group.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "所选车型不可用");
        }
        return buildQuote(requirement, rentalCity, cityMatch, group, template);
    }

    private void validateRentalRequirement(TravelRequirementDTO requirement) {
        if (requirement == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "租车报价需求不能为空");
        }
        RentalRequirementDTO rental = requirement.rentalRequirement();
        boolean needRental =
                rental != null && Boolean.TRUE.equals(rental.needRental())
                        || "ROAD_TRIP".equals(requirement.routeMode())
                        || "LANDING_RENTAL_TRIP".equals(requirement.routeMode())
                        || "USER_REQUIRED".equals(requirement.rentalIntent());
        if (!needRental) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "当前需求未选择租车");
        }
        if (requirement.days() == null || requirement.days() < 1 || requirement.days() > 7) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "租车天数必须在 1 到 7 天之间");
        }
    }

    private String resolveRentalCity(TravelRequirementDTO requirement) {
        RentalRequirementDTO rental = requirement.rentalRequirement();
        if (rental != null && notBlank(rental.rentalStartCity())) {
            return rental.rentalStartCity();
        }
        if ("ROAD_TRIP".equals(requirement.routeMode())) {
            return requirement.departure();
        }
        return requirement.destination();
    }

    private CityMatch resolveCityMatch(String rentalCity) {
        if (isBlank(rentalCity)) {
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
        if (notBlank(cityMatch.citycode())) {
            query.eq(RentalPriceTemplate::getCitycode, cityMatch.citycode());
        } else {
            query.eq(RentalPriceTemplate::getCity, cityMatch.city());
        }
        List<RentalPriceTemplate> templates = priceTemplateMapper.selectList(query);
        if (templates.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "当前城市暂无租车价格模板：" + cityMatch.city());
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
        int people = requirement.peopleCount() == null ? 2 : requirement.peopleCount();
        String vehiclePreference =
                requirement.rentalRequirement() == null
                        ? ""
                        : safe(requirement.rentalRequirement().vehiclePreference());
        List<String> preferences =
                requirement.preferences() == null ? List.of() : requirement.preferences();
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
        String code = safe(group.getGroupCode()).toUpperCase(Locale.ROOT);
        int index = preferredCodes.indexOf(code);
        int score = index >= 0 ? 100 - index * 15 : 20;
        if (requirement.budget() != null
                && requirement.budget() >= 8000
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
        RentalRequirementDTO rental = requirement.rentalRequirement();
        String pickupMode =
                rental == null ? "UNKNOWN" : safeDefault(rental.pickupMode(), "UNKNOWN");
        String returnMode =
                rental == null ? pickupMode : safeDefault(rental.returnMode(), pickupMode);
        RentalPickupPoi pickupPoi = choosePoi(cityMatch.pois(), pickupMode);
        RentalPickupPoi returnPoi = choosePoi(cityMatch.pois(), returnMode);
        int rentalDays =
                rental != null && rental.rentalDays() != null
                        ? rental.rentalDays()
                        : requirement.days();
        boolean isOneWay = rental != null && Boolean.TRUE.equals(rental.isOneWay());
        boolean delivery = rental != null && Boolean.TRUE.equals(rental.deliveryRequired());

        RentalFeeBreakdownDTO fee =
                calculateFee(requirement, template, rentalDays, isOneWay, delivery);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("source", "rental_price_template");
        snapshot.put("templateId", template.getId());
        snapshot.put("city", rentalCity);
        snapshot.put("citycode", cityMatch.citycode());
        snapshot.put("vehicleGroupId", group.getId());
        snapshot.put("groupCode", group.getGroupCode());
        snapshot.put("rentalDays", rentalDays);
        snapshot.put("feeBreakdown", fee);

        return new RentalQuoteOptionDTO(
                "Q-" + template.getId() + "-" + group.getId(),
                requirement.routeMode(),
                rentalCity,
                cityMatch.citycode(),
                cityMatch.adcode(),
                group.getId(),
                group.getGroupCode(),
                group.getGroupName(),
                group.getDisplayName(),
                group.getVehicleClass(),
                group.getEnergyType(),
                group.getSeatsMin(),
                group.getSeatsMax(),
                pickupPoi == null ? null : pickupPoi.getId(),
                pickupPoi == null ? null : pickupPoi.getPoiName(),
                pickupPoi == null ? null : pickupPoi.getAddress(),
                returnPoi == null ? null : returnPoi.getId(),
                returnPoi == null ? null : returnPoi.getPoiName(),
                returnPoi == null ? null : returnPoi.getAddress(),
                pickupMode,
                returnMode,
                rentalDays,
                isOneWay,
                template.getId(),
                fee,
                snapshot);
    }

    private RentalFeeBreakdownDTO calculateFee(
            TravelRequirementDTO requirement,
            RentalPriceTemplate template,
            int rentalDays,
            boolean isOneWay,
            boolean delivery) {
        int rentalFee = 0;
        LocalDate start = parseDate(requirement.travelDate());
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
        if (pois == null || pois.isEmpty()) {
            return null;
        }
        Comparator<RentalPickupPoi> comparator =
                Comparator.comparingInt((RentalPickupPoi poi) -> poiScore(poi, mode));
        return pois.stream().max(comparator).orElse(pois.get(0));
    }

    private int poiScore(RentalPickupPoi poi, String mode) {
        String name = safe(poi.getPoiName());
        String type = safe(poi.getPoiType()) + safe(poi.getPoiTypecode());
        if ("AIRPORT".equals(mode)) {
            return containsAny(name + type, "机场", "150104") ? 100 : 1;
        }
        if ("TRAIN_STATION".equals(mode)) {
            return containsAny(name + type, "高铁", "火车", "车站", "150200") ? 100 : 1;
        }
        return containsAny(name, "市区", "中心") ? 60 : 10;
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
        return city == null ? "" : city.replace("市", "").trim();
    }

    private LocalDate parseDate(String value) {
        try {
            return isBlank(value) ? LocalDate.now() : LocalDate.parse(value);
        } catch (Exception e) {
            return LocalDate.now();
        }
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean containsAny(String input, String... values) {
        for (String value : values) {
            if (input.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean notBlank(String value) {
        return !isBlank(value);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String safeDefault(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }

    private record CityMatch(
            String city, String citycode, String adcode, List<RentalPickupPoi> pois) {}
}
