package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.dto.model.poi.Poi;
import com.sora.aitravel.service.AmapPoiCacheService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 查询目的地基础 POI 数据池：景点、餐饮区域和住宿区域。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CityDataProfileNode {

    private static final int MAX_CANDIDATES = 80;
    private static final int MIN_SCENIC_PER_DAY = 4;
    private static final String POI_SHOW_FIELDS = "business,navi,photos";

    private final AmapPoiCacheService amapPoiCacheService;

    public void execute(GenerateWorkflowContext context) {
        TravelRequirementDTO requirement = context.getRequirement();
        String destination = displayDestination(requirement);

        List<PoiCandidate> scenicCandidates =
                ensureEnoughScenicCandidates(
                        destination, requirement, searchScenicCandidates(destination, requirement));
        List<PoiCandidate> foodCandidates =
                searchMany(foodKeywords(destination), destination, "FOOD", "适合安排顺路餐饮，减少跨区域移动。");
        List<PoiCandidate> hotelCandidates =
                searchMany(
                        List.of(destination + " 商圈", destination + " 酒店", destination + " 地铁站"),
                        destination,
                        "HOTEL",
                        "适合住宿，交通和餐饮相对便利。");

        CityProfile profile =
                new CityProfile(
                        destination,
                        popularAreas(destination, hotelCandidates, scenicCandidates),
                        List.of(destination + "火车站", destination + "机场"),
                        ensureCandidates(destination, "SCENIC", scenicCandidates),
                        ensureCandidates(destination, "FOOD", foodCandidates),
                        ensureCandidates(destination, "HOTEL", hotelCandidates));
        context.setCityProfile(profile);
        log.info(
                "节点[city-data-profile]：城市数据准备完成，destination={}, scenic={}, food={}, hotel={}",
                destination,
                profile.scenicCandidates().size(),
                profile.foodCandidates().size(),
                profile.hotelCandidates().size());
    }

    private List<PoiCandidate> searchScenicCandidates(
            String destination, TravelRequirementDTO requirement) {
        List<String> keywords = scenicKeywords(destination, requirement);
        List<PoiCandidate> candidates =
                searchMany(keywords, destination, "SCENIC", scenicReason(requirement));
        log.info("节点[city-data-profile]：景点关键词={}，候选数={}", keywords, candidates.size());
        return candidates;
    }

    private List<PoiCandidate> ensureEnoughScenicCandidates(
            String destination,
            TravelRequirementDTO requirement,
            List<PoiCandidate> initialCandidates) {
        int minimum = Math.max(2, nullToOne(requirement.getDays()) * MIN_SCENIC_PER_DAY);
        Map<String, PoiCandidate> candidates = new LinkedHashMap<>();
        addCandidates(candidates, initialCandidates, destination);
        if (candidates.size() >= minimum) {
            return candidates.values().stream().limit(MAX_CANDIDATES).toList();
        }

        List<String> retryKeywords =
                List.of(
                        destination + " 旅游景点",
                        destination + " 景区",
                        destination + " 旅游",
                        destination + " 文化",
                        destination + " 古街",
                        destination + " 寺庙",
                        destination + " 展览馆",
                        destination + " 购物街",
                        destination + " 商圈",
                        destination + " 网红打卡");
        for (String keyword : retryKeywords) {
            addCandidates(
                    candidates,
                    searchDeep(keyword, destination, "SCENIC", scenicReason(requirement)),
                    destination);
            if (candidates.size() >= minimum) {
                break;
            }
        }

        log.info(
                "节点[city-data-profile]：景点候选补查完成，minimum={}, actual={}", minimum, candidates.size());
        return ensureMinimumMockCandidates(
                destination, candidates.values().stream().limit(MAX_CANDIDATES).toList(), minimum);
    }

    private void addCandidates(
            Map<String, PoiCandidate> target, List<PoiCandidate> candidates, String destination) {
        for (PoiCandidate candidate : candidates) {
            if (isUsableCandidate(candidate, destination)) {
                target.putIfAbsent(dedupKey(candidate), candidate);
            }
        }
    }

    private List<PoiCandidate> searchMany(
            List<String> keywords, String region, String category, String defaultReason) {
        Map<String, PoiCandidate> deduped = new LinkedHashMap<>();
        for (String keyword : keywords) {
            for (PoiCandidate candidate : search(keyword, region, category, defaultReason)) {
                if (isUsableCandidate(candidate, region)) {
                    deduped.putIfAbsent(dedupKey(candidate), candidate);
                }
            }
        }
        return deduped.values().stream().limit(MAX_CANDIDATES).toList();
    }

    private List<PoiCandidate> search(
            String keywords, String region, String category, String defaultReason) {
        try {
            List<PoiCandidate> candidates = new ArrayList<>();
            for (int page = 1; page <= 2; page++) {
                List<Poi> pagePois =
                        amapPoiCacheService.searchText(
                                keywords, null, region, true, 25, page, POI_SHOW_FIELDS, category);
                if (pagePois.isEmpty()) {
                    break;
                }
                candidates.addAll(
                        pagePois.stream()
                                .filter(poi -> poi.getName() != null && poi.getLocation() != null)
                                .map(poi -> toCandidate(category, poi, defaultReason))
                                .toList());
            }
            return candidates.stream().limit(MAX_CANDIDATES).toList();
        } catch (RuntimeException exception) {
            log.warn(
                    "节点[city-data-profile]：高德 POI 查询失败，keywords={}，reason={}",
                    keywords,
                    exception.getMessage());
        }
        return List.of();
    }

    private List<PoiCandidate> searchDeep(
            String keywords, String region, String category, String defaultReason) {
        try {
            List<PoiCandidate> candidates = new ArrayList<>();
            for (int page = 1; page <= 5; page++) {
                List<Poi> pagePois =
                        amapPoiCacheService.searchText(
                                keywords, null, region, true, 25, page, POI_SHOW_FIELDS, category);
                if (pagePois.isEmpty()) {
                    break;
                }
                candidates.addAll(
                        pagePois.stream()
                                .filter(poi -> poi.getName() != null && poi.getLocation() != null)
                                .map(poi -> toCandidate(category, poi, defaultReason))
                                .toList());
            }
            return candidates.stream().limit(MAX_CANDIDATES).toList();
        } catch (RuntimeException exception) {
            log.warn(
                    "节点[city-data-profile]：高德 POI 补查失败，keywords={}，reason={}",
                    keywords,
                    exception.getMessage());
        }
        return List.of();
    }

    private List<String> scenicKeywords(String destination, TravelRequirementDTO requirement) {
        List<String> keywords = new ArrayList<>();
        keywords.add(destination + " 必游景点");
        keywords.add(destination + " 城市地标");
        keywords.add(destination + " 博物馆");
        keywords.add(destination + " 历史街区");
        keywords.add(destination + " 公园");
        keywords.add(destination + " 名胜古迹");
        if (hasPreference(requirement, "自然") || hasPreference(requirement, "nature")) {
            keywords.add(destination + " 湿地");
            keywords.add(destination + " 风景名胜");
        }
        if (hasPreference(requirement, "历史") || hasPreference(requirement, "culture")) {
            keywords.add(destination + " 古迹");
            keywords.add(destination + " 寺庙");
        }
        return keywords.stream().distinct().toList();
    }

    private List<String> foodKeywords(String destination) {
        return List.of(destination + " 美食街", destination + " 餐厅", destination + " 老字号");
    }

    private PoiCandidate toCandidate(String category, Poi poi, String reason) {
        return new PoiCandidate(
                category,
                poi.getName(),
                poi.getAddress(),
                firstNonBlank(poi.getAdname(), poi.getCityname()),
                poi.getLocation(),
                "AMAP",
                poi.getId(),
                reason,
                parseInteger(poi.getDistance()),
                poi.getTypecode(),
                poi.getParent(),
                poi.getBusiness() == null
                        ? null
                        : firstNonBlank(
                                poi.getBusiness().getOpentimeToday(),
                                poi.getBusiness().getOpentimeWeek()),
                poi.getBusiness() == null ? null : poi.getBusiness().getRating(),
                poi.getBusiness() == null ? null : parseDecimalInteger(poi.getBusiness().getCost()),
                poi.getBusiness() == null ? null : poi.getBusiness().getBusinessArea(),
                poi.getBusiness() == null ? List.of() : splitTags(poi.getBusiness().getTag()),
                poi.getNavi() == null ? null : poi.getNavi().getEntrLocation(),
                poi.getPhotos() == null
                        ? List.of()
                        : poi.getPhotos().stream()
                                .map(item -> item.getUrl())
                                .filter(item -> item != null && !item.isBlank())
                                .limit(3)
                                .toList());
    }

    private boolean isUsableCandidate(PoiCandidate candidate, String destination) {
        if (candidate == null || candidate.getName() == null || candidate.getLocation() == null) {
            return false;
        }
        String name = candidate.getName();
        if (name.contains("停车场")
                || name.contains("游客中心")
                || name.contains("入口")
                || name.contains("售票")
                || name.contains("卫生间")
                || name.contains("公交站")) {
            return false;
        }
        if (candidate.getAddress() != null
                && !candidate.getAddress().isBlank()
                && destination != null
                && !candidate.getAddress().contains(destination)
                && candidate.getArea() != null
                && !candidate.getArea().contains(destination)) {
            return false;
        }
        return true;
    }

    private String dedupKey(PoiCandidate candidate) {
        String name = candidate.getName() == null ? "" : candidate.getName();
        return name.replace("景区", "")
                .replace("风景区", "")
                .replace("步行街", "")
                .replace("历史文化特色街区", "历史街区")
                .toLowerCase(Locale.ROOT);
    }

    private List<PoiCandidate> ensureCandidates(
            String destination, String category, List<PoiCandidate> candidates) {
        if (candidates != null && !candidates.isEmpty()) {
            return candidates;
        }
        return switch (category) {
            case "FOOD" ->
                    List.of(
                            mock(
                                    category,
                                    destination + "特色餐饮街区",
                                    destination + "核心区域",
                                    "适合穿插午餐或晚餐，减少额外绕路。"));
            case "HOTEL" ->
                    List.of(
                            mock(
                                    category,
                                    destination + "核心商圈住宿区域",
                                    destination + "核心区域",
                                    "交通和餐饮选择相对集中，适合作为住宿区域。"));
            default ->
                    List.of(
                            mock(
                                    category,
                                    destination + "城市地标",
                                    destination + "核心区域",
                                    "适合作为城市开场的代表性体验。"),
                            mock(
                                    category,
                                    destination + "历史文化街区",
                                    destination + "老城区域",
                                    "适合安排慢走和人文体验。"));
        };
    }

    private List<PoiCandidate> ensureMinimumMockCandidates(
            String destination, List<PoiCandidate> candidates, int minimum) {
        if (candidates.size() >= minimum) {
            return candidates;
        }
        List<PoiCandidate> result = new ArrayList<>(candidates);
        List<String> fallbackNames =
                List.of("城市地标", "历史文化街区", "城市公园", "博物馆", "特色商圈", "老街步行区", "文化广场", "滨水休闲区");
        int index = 0;
        while (result.size() < minimum) {
            String name =
                    destination
                            + fallbackNames.get(index % fallbackNames.size())
                            + (index / fallbackNames.size() + 1);
            result.add(mock("SCENIC", name, destination + "核心区域", "适合作为当天路线的补充体验。"));
            index++;
        }
        return result;
    }

    private PoiCandidate mock(String category, String name, String area, String reason) {
        return new PoiCandidate(
                category,
                name,
                area + "待确认地址",
                area,
                null,
                "MOCK",
                "MOCK_" + Math.abs(name.hashCode()),
                reason,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                List.of());
    }

    private List<String> popularAreas(
            String destination,
            List<PoiCandidate> hotelCandidates,
            List<PoiCandidate> scenicCandidates) {
        List<String> areas =
                hotelCandidates.stream()
                        .map(PoiCandidate::area)
                        .filter(item -> item != null && !item.isBlank())
                        .distinct()
                        .limit(3)
                        .toList();
        if (!areas.isEmpty()) {
            return areas;
        }
        return scenicCandidates.stream()
                .map(PoiCandidate::area)
                .filter(item -> item != null && !item.isBlank())
                .distinct()
                .limit(3)
                .toList();
    }

    private String scenicReason(TravelRequirementDTO requirement) {
        if (requirement.getPreferences() == null || requirement.getPreferences().isEmpty()) {
            return "适合作为目的地代表性景点。";
        }
        return "匹配用户偏好：" + String.join("、", requirement.getPreferences()) + "。";
    }

    private boolean hasPreference(TravelRequirementDTO requirement, String keyword) {
        return requirement.getPreferences() != null
                && requirement.getPreferences().stream()
                        .anyMatch(
                                item ->
                                        item.toLowerCase(Locale.ROOT)
                                                .contains(keyword.toLowerCase(Locale.ROOT)));
    }

    private int nullToOne(Integer value) {
        return value == null || value < 1 ? 1 : value;
    }

    private String displayDestination(TravelRequirementDTO requirement) {
        if (requirement.getDestination() != null && !requirement.getDestination().isBlank()) {
            return requirement.getDestination();
        }
        if (requirement.getRouteRegion() != null && !requirement.getRouteRegion().isBlank()) {
            return requirement.getRouteRegion();
        }
        return String.join("-", requirement.getRouteCities());
    }

    private Integer parseInteger(String value) {
        try {
            return value == null || value.isBlank() ? null : Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Integer parseDecimalInteger(String value) {
        try {
            return value == null || value.isBlank()
                    ? null
                    : new java.math.BigDecimal(value)
                            .setScale(0, java.math.RoundingMode.HALF_UP)
                            .intValue();
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private List<String> splitTags(String value) {
        return value == null || value.isBlank()
                ? List.of()
                : java.util.Arrays.stream(value.split(","))
                        .map(String::trim)
                        .filter(item -> !item.isBlank())
                        .limit(8)
                        .toList();
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
}
