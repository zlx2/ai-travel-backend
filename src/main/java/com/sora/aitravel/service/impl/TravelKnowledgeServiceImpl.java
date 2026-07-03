package com.sora.aitravel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sora.aitravel.common.utils.JsonCodec;
import com.sora.aitravel.entity.TravelArea;
import com.sora.aitravel.entity.TravelCity;
import com.sora.aitravel.entity.TravelSpot;
import com.sora.aitravel.mapper.TravelAreaMapper;
import com.sora.aitravel.mapper.TravelCityMapper;
import com.sora.aitravel.mapper.TravelSpotMapper;
import com.sora.aitravel.model.AreaAnchorCandidate;
import com.sora.aitravel.model.PoiCandidate;
import com.sora.aitravel.service.TravelKnowledgeService;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Reads normalized travel-city/area/spot tables for AI generation. */
@Service
@RequiredArgsConstructor
public class TravelKnowledgeServiceImpl implements TravelKnowledgeService {

    private final TravelCityMapper travelCityMapper;
    private final TravelAreaMapper travelAreaMapper;
    private final TravelSpotMapper travelSpotMapper;
    private final JsonCodec jsonCodec;

    @Override
    public List<String> supportedCities(List<String> requestedCities) {
        return findCities(requestedCities).stream().map(TravelCity::getCityName).toList();
    }

    @Override
    public List<PoiCandidate> scenicCandidates(List<String> requestedCities) {
        KnowledgeRows rows = loadRows(requestedCities);
        if (rows.cities().isEmpty() || rows.spots().isEmpty()) {
            return List.of();
        }
        Map<Long, TravelCity> cities = cityMap(rows.cities());
        return rows.spots().stream()
                .sorted(
                        Comparator.comparing(
                                        TravelSpot::getValueLevel,
                                        Comparator.nullsLast(Comparator.naturalOrder()))
                                .thenComparing(
                                        TravelSpot::getQualityScore,
                                        Comparator.nullsLast(Comparator.reverseOrder()))
                                .thenComparing(
                                        TravelSpot::getPopularityScore,
                                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(spot -> toCandidate(spot, cities.get(spot.getCityId())))
                .toList();
    }

    @Override
    public List<AreaAnchorCandidate> areaAnchors(List<String> requestedCities) {
        KnowledgeRows rows = loadRows(requestedCities);
        if (rows.cities().isEmpty() || rows.areas().isEmpty()) {
            return List.of();
        }
        Map<Long, TravelCity> cities = cityMap(rows.cities());
        return rows.areas().stream()
                .sorted(
                        Comparator.comparing(
                                        TravelArea::getExclusiveDay,
                                        Comparator.nullsLast(Comparator.reverseOrder()))
                                .thenComparing(
                                        TravelArea::getPriorityScore,
                                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(area -> toAnchor(area, cities.get(area.getCityId())))
                .toList();
    }

    @Override
    public void cacheAmapScenicCandidates(String cityName, List<PoiCandidate> candidates) {
        if (cityName == null || cityName.isBlank() || candidates == null || candidates.isEmpty()) {
            return;
        }
        TravelCity city = upsertCity(cityName, candidates);
        for (PoiCandidate candidate : candidates) {
            if (!isCacheableScenic(candidate)) {
                continue;
            }
            TravelArea area = upsertArea(city, candidate);
            upsertSpot(city, area, candidate);
        }
    }

    private KnowledgeRows loadRows(List<String> requestedCities) {
        List<TravelCity> cities = findCities(requestedCities);
        if (cities.isEmpty()) {
            return new KnowledgeRows(List.of(), List.of(), List.of());
        }
        List<Long> cityIds = cities.stream().map(TravelCity::getId).toList();
        List<TravelArea> areas =
                travelAreaMapper.selectList(
                        new LambdaQueryWrapper<TravelArea>()
                                .in(TravelArea::getCityId, cityIds)
                                .orderByDesc(TravelArea::getPriorityScore));
        List<TravelSpot> spots =
                travelSpotMapper.selectList(
                        new LambdaQueryWrapper<TravelSpot>()
                                .in(TravelSpot::getCityId, cityIds)
                                .orderByAsc(TravelSpot::getValueLevel)
                                .orderByDesc(TravelSpot::getQualityScore)
                                .orderByDesc(TravelSpot::getPopularityScore));
        return new KnowledgeRows(cities, areas, spots);
    }

    private List<TravelCity> findCities(List<String> requestedCities) {
        if (requestedCities == null || requestedCities.isEmpty()) {
            return List.of();
        }
        List<String> names =
                requestedCities.stream()
                        .map(this::normalizeCity)
                        .filter(item -> !item.isBlank())
                        .distinct()
                        .toList();
        if (names.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<TravelCity> query = new LambdaQueryWrapper<>();
        for (String name : names) {
            query.or(
                    wrapper ->
                            wrapper.eq(TravelCity::getCityName, name)
                                    .or()
                                    .eq(TravelCity::getCityName, name + "市")
                                    .or()
                                    .like(TravelCity::getCityName, name));
        }
        return travelCityMapper.selectList(query);
    }

    private TravelCity upsertCity(String cityName, List<PoiCandidate> candidates) {
        String normalized = normalizeCity(cityName);
        TravelCity existing =
                travelCityMapper.selectOne(
                        new LambdaQueryWrapper<TravelCity>()
                                .eq(TravelCity::getCityName, normalized)
                                .or()
                                .eq(TravelCity::getCityName, normalized + "市")
                                .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }
        TravelCity city = new TravelCity();
        city.setCityName(normalized);
        candidates.stream()
                .map(this::parseLocation)
                .filter(item -> item != null)
                .findFirst()
                .ifPresent(
                        location -> {
                            city.setCenterLng(location[0]);
                            city.setCenterLat(location[1]);
                        });
        travelCityMapper.insert(city);
        return city;
    }

    private TravelArea upsertArea(TravelCity city, PoiCandidate candidate) {
        String areaName = firstNonBlank(candidate.getBusinessArea(), candidate.getArea(), "核心游览区");
        TravelArea existing =
                travelAreaMapper.selectOne(
                        new LambdaQueryWrapper<TravelArea>()
                                .eq(TravelArea::getCityId, city.getId())
                                .eq(TravelArea::getAreaName, areaName)
                                .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }
        BigDecimal[] location = parseLocation(candidate);
        TravelArea area = new TravelArea();
        area.setCityId(city.getId());
        area.setAreaName(areaName);
        area.setCenterLng(location == null ? city.getCenterLng() : location[0]);
        area.setCenterLat(location == null ? city.getCenterLat() : location[1]);
        area.setRadiusKm(BigDecimal.valueOf(0.8));
        area.setAreaType(areaType(candidate));
        area.setRingLevel("INNER");
        area.setTagsJson(jsonCodec.write(candidate.getBusinessTags(), "旅行片区标签序列化失败"));
        area.setPriorityScore(areaPriority(candidate));
        area.setSuggestedDurationHours(BigDecimal.valueOf(3.0));
        area.setExclusiveDay(0);
        travelAreaMapper.insert(area);
        return area;
    }

    private void upsertSpot(TravelCity city, TravelArea area, PoiCandidate candidate) {
        String sourcePoiId = candidate.getSourcePoiId();
        TravelSpot existing =
                travelSpotMapper.selectOne(
                        new LambdaQueryWrapper<TravelSpot>()
                                .eq(TravelSpot::getSource, "AMAP")
                                .eq(TravelSpot::getSourcePoiId, sourcePoiId)
                                .last("LIMIT 1"));
        if (existing != null) {
            return;
        }
        BigDecimal[] location = parseLocation(candidate);
        if (location == null) {
            return;
        }
        TravelSpot spot = new TravelSpot();
        spot.setCityId(city.getId());
        spot.setAreaId(area.getId());
        spot.setSource("AMAP");
        spot.setSourcePoiId(sourcePoiId);
        spot.setSpotName(candidate.getName());
        spot.setAliasJson("[]");
        spot.setLng(location[0]);
        spot.setLat(location[1]);
        spot.setAddress(candidate.getAddress());
        spot.setSpotType(spotType(candidate));
        spot.setTagsJson(jsonCodec.write(candidate.getBusinessTags(), "旅行景点标签序列化失败"));
        spot.setValueLevel(valueLevel(candidate));
        spot.setQualityScore(qualityScore(candidate));
        spot.setPopularityScore(popularityScore(candidate));
        spot.setRecommendedDurationMin(120);
        spot.setBestTimeJson("[]");
        spot.setOpenTimeText(candidate.getOpeningHours());
        spot.setPhysicalLevel("LOW");
        spot.setRainFriendly(isIndoor(candidate) ? 1 : 0);
        spot.setNightFriendly(isNightFriendly(candidate) ? 1 : 0);
        spot.setFamilyFriendly(hasTag(candidate, "亲子") ? 1 : 0);
        travelSpotMapper.insert(spot);
    }

    private PoiCandidate toCandidate(TravelSpot spot, TravelCity city) {
        List<String> tags = parseStringList(spot.getTagsJson());
        String cityName = city == null ? null : city.getCityName();
        String fallbackArea = cityName == null ? "热门景点" : cityName + "热门景点";
        return new PoiCandidate(
                "SCENIC",
                spot.getSpotName(),
                spot.getAddress(),
                fallbackArea,
                cityName,
                location(spot.getLng(), spot.getLat()),
                "TRAVEL_SPOT",
                String.valueOf(spot.getId()),
                candidateReason(spot),
                null,
                spot.getSpotType(),
                null,
                spot.getOpenTimeText(),
                null,
                null,
                fallbackArea,
                tags,
                null,
                List.of());
    }

    private AreaAnchorCandidate toAnchor(TravelArea area, TravelCity city) {
        return new AreaAnchorCandidate(
                "TRAVEL_AREA_" + area.getId(),
                area.getAreaName(),
                "SCENIC_CLUSTER",
                city == null ? null : city.getCityName(),
                area.getAreaName(),
                null,
                location(area.getCenterLng(), area.getCenterLat()),
                "TRAVEL_AREA",
                String.valueOf(area.getId()),
                parseStringList(area.getTagsJson()));
    }

    private String candidateReason(TravelSpot spot) {
        return "规范景点库 value="
                + spot.getValueLevel()
                + ", quality="
                + spot.getQualityScore()
                + ", popularity="
                + spot.getPopularityScore();
    }

    private boolean isCacheableScenic(PoiCandidate candidate) {
        return candidate != null
                && "SCENIC".equals(candidate.getCategory())
                && candidate.getName() != null
                && !candidate.getName().isBlank()
                && !isNicheVehicleMuseum(candidate)
                && candidate.getLocation() != null
                && !candidate.getLocation().isBlank()
                && candidate.getSourcePoiId() != null
                && !candidate.getSourcePoiId().isBlank()
                && qualityScore(candidate) >= 55;
    }

    private String spotType(PoiCandidate candidate) {
        String typeCode = candidate.getTypeCode() == null ? "" : candidate.getTypeCode();
        String name = candidate.getName() == null ? "" : candidate.getName();
        if (typeCode.startsWith("1401") || name.contains("博物馆") || name.contains("展览馆")) {
            return "MUSEUM";
        }
        if (name.contains("公园") || name.contains("植物园")) {
            return "PARK";
        }
        if (name.contains("寺") || name.contains("庙") || name.contains("祠")) {
            return "TEMPLE";
        }
        if (name.contains("古镇") || name.contains("古街") || name.contains("老街")) {
            return "OLD_STREET";
        }
        if (name.contains("山") || name.contains("湖") || name.contains("湿地")) {
            return "NATURE";
        }
        if (name.contains("乐园") || name.contains("海洋")) {
            return "THEME_PARK";
        }
        return "LANDMARK";
    }

    private String areaType(PoiCandidate candidate) {
        String spotType = spotType(candidate);
        return switch (spotType) {
            case "MUSEUM" -> "MUSEUM";
            case "NATURE", "PARK" -> "NATURE";
            case "THEME_PARK" -> "THEME_PARK";
            case "OLD_STREET", "TEMPLE" -> "HISTORIC";
            default -> "CORE";
        };
    }

    private String valueLevel(PoiCandidate candidate) {
        int score = qualityScore(candidate);
        if (score >= 88) {
            return "S";
        }
        if (score >= 74) {
            return "A";
        }
        return "B";
    }

    private int qualityScore(PoiCandidate candidate) {
        int score = 60;
        if (candidate.getRating() != null) {
            try {
                score +=
                        new BigDecimal(candidate.getRating()).multiply(BigDecimal.TEN).intValue()
                                - 35;
            } catch (NumberFormatException ignored) {
                score += 0;
            }
        }
        if (candidate.getImageUrls() != null && !candidate.getImageUrls().isEmpty()) {
            score += 5;
        }
        if (candidate.getOpeningHours() != null && !candidate.getOpeningHours().isBlank()) {
            score += 5;
        }
        if (hasTag(candidate, "免费") || hasTag(candidate, "夜景") || hasTag(candidate, "亲子")) {
            score += 5;
        }
        return Math.max(0, Math.min(100, score));
    }

    private int popularityScore(PoiCandidate candidate) {
        int score = 50;
        if (candidate.getRating() != null) {
            try {
                score +=
                        new BigDecimal(candidate.getRating()).multiply(BigDecimal.TEN).intValue()
                                - 35;
            } catch (NumberFormatException ignored) {
                score += 0;
            }
        }
        return Math.max(0, Math.min(100, score));
    }

    private int areaPriority(PoiCandidate candidate) {
        return Math.max(60, Math.min(95, qualityScore(candidate)));
    }

    private boolean isIndoor(PoiCandidate candidate) {
        return hasTag(candidate, "博物馆")
                || contains(candidate.getName(), "博物馆")
                || contains(candidate.getName(), "展览馆");
    }

    private boolean isNightFriendly(PoiCandidate candidate) {
        return hasTag(candidate, "夜景")
                || contains(candidate.getName(), "夜")
                || contains(candidate.getName(), "街");
    }

    private boolean isNicheVehicleMuseum(PoiCandidate candidate) {
        String text =
                (candidate.getName() == null ? "" : candidate.getName())
                        + " "
                        + String.join(
                                " ",
                                candidate.getBusinessTags() == null
                                        ? List.of()
                                        : candidate.getBusinessTags());
        return text.contains("博物馆") && containsAny(text, "老爷车", "汽车", "车文化", "摩托车", "房车");
    }

    private boolean hasTag(PoiCandidate candidate, String tag) {
        return candidate.getBusinessTags() != null
                && candidate.getBusinessTags().stream().anyMatch(item -> item.contains(tag));
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.contains(keyword);
    }

    private boolean containsAny(String value, String... keywords) {
        if (value == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal[] parseLocation(PoiCandidate candidate) {
        if (candidate == null || candidate.getLocation() == null) {
            return null;
        }
        String[] parts = candidate.getLocation().split(",");
        if (parts.length != 2) {
            return null;
        }
        try {
            return new BigDecimal[] {new BigDecimal(parts[0]), new BigDecimal(parts[1])};
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<?> values = jsonCodec.read(json, List.class, "旅行知识库标签解析失败");
            return values.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(item -> !item.isBlank())
                    .limit(12)
                    .toList();
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    private String normalizeCity(String city) {
        return city == null ? "" : city.replace("市", "").trim();
    }

    private String location(BigDecimal lng, BigDecimal lat) {
        if (lng == null || lat == null) {
            return null;
        }
        return lng.toPlainString() + "," + lat.toPlainString();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private Map<Long, TravelCity> cityMap(List<TravelCity> cities) {
        Map<Long, TravelCity> map = new LinkedHashMap<>();
        cities.forEach(city -> map.put(city.getId(), city));
        return map;
    }

    private Map<Long, TravelArea> areaMap(List<TravelArea> areas) {
        Map<Long, TravelArea> map = new LinkedHashMap<>();
        areas.forEach(area -> map.put(area.getId(), area));
        return map;
    }

    private record KnowledgeRows(
            List<TravelCity> cities, List<TravelArea> areas, List<TravelSpot> spots) {}
}
