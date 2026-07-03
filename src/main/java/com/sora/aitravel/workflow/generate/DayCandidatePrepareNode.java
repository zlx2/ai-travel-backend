package com.sora.aitravel.workflow.generate;

import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.CITY_PROFILE;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.DAY_CONTEXTS;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.DAY_QUERY_PLANS;
import static com.sora.aitravel.workflow.generate.TripGraphStateKeys.RANKED_DAY_DATA_PACKAGES;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.sora.aitravel.dto.model.poi.Poi;
import com.sora.aitravel.model.CityProfile;
import com.sora.aitravel.model.DayContext;
import com.sora.aitravel.model.DayDataPackage;
import com.sora.aitravel.model.DayQueryPlan;
import com.sora.aitravel.model.PoiCandidate;
import com.sora.aitravel.model.QueryItem;
import com.sora.aitravel.service.AmapPoiCacheService;
import com.sora.aitravel.service.impl.PoiIdentityServiceImpl;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 准备单日候选数据。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DayCandidatePrepareNode {

    private static final int MAX_DAY_CANDIDATES = 50;
    private static final int MAX_RANKED_CANDIDATES = 40;
    private static final int MAX_CURATED_FALLBACK_NORMAL = 4;
    private static final int MAX_CURATED_FALLBACK_LOW_PRIMARY = 6;
    private static final int MAX_CURATED_FALLBACK_EMPTY_PRIMARY = 10;
    private static final String POI_SHOW_FIELDS = "business,navi,photos";

    private final AmapPoiCacheService amapPoiCacheService;
    private final PoiIdentityServiceImpl poiIdentityService;

    @Value("${app.amap.day-query-parallelism:6}")
    private int dayQueryParallelism;

    public Map<String, Object> execute(OverAllState state) {
        Map<String, Object> patch = new LinkedHashMap<>();
        List<DayDataPackage> packages =
                fetchDataPackages(
                        TripGraphStateCodec.optionalList(
                                state, DAY_QUERY_PLANS, DayQueryPlan.class),
                        TripGraphStateCodec.optionalList(state, DAY_CONTEXTS, DayContext.class),
                        TripGraphStateCodec.required(state, CITY_PROFILE, CityProfile.class));
        patch.put(
                RANKED_DAY_DATA_PACKAGES,
                rankPackages(
                        packages,
                        TripGraphStateCodec.optionalList(state, DAY_CONTEXTS, DayContext.class)));
        return patch;
    }

    private List<DayDataPackage> fetchDataPackages(
            List<DayQueryPlan> dayQueryPlans,
            List<DayContext> dayContexts,
            CityProfile cityProfile) {
        List<DayDataPackage> packages = new ArrayList<>();
        for (DayQueryPlan plan : dayQueryPlans) {
            List<List<PoiCandidate>> scenicBatches = new ArrayList<>();
            List<PoiCandidate> night = new ArrayList<>();
            List<PoiCandidate> food = new ArrayList<>();
            List<QuerySearchResult> searchResults = searchQueries(plan);
            for (QuerySearchResult searchResult : searchResults) {
                QueryItem query = searchResult.query();
                if (isScenicQuery(query)) {
                    scenicBatches.add(searchResult.candidates());
                } else if ("NIGHT".equals(query.getType())) {
                    night.addAll(searchResult.candidates());
                } else if ("FOOD".equals(query.getType())) {
                    food.addAll(searchResult.candidates());
                }
            }
            List<PoiCandidate> scenic = new ArrayList<>();
            scenic.addAll(roundRobin(scenicBatches).stream().limit(42).toList());
            scenic.addAll(deduplicate(night).stream().limit(8).toList());
            String dayCity = dayCity(dayContexts, plan.getDay());
            packages.add(
                    new DayDataPackage(
                            plan.getDay(),
                            mergeScenicCandidates(
                                    scenic, cityCandidates(cityProfile.scenicCandidates(), dayCity)),
                            merge(cityCandidates(cityProfile.foodCandidates(), dayCity), food),
                            cityCandidates(cityProfile.hotelCandidates(), dayCity),
                            List.of()));
        }
        log.info(
                "节点[day-candidate-prepare]：已执行每天 POI 查询，days={}, scenicCounts={}",
                packages.size(),
                packages.stream().map(item -> item.scenicCandidates().size()).toList());
        return packages;
    }

    private List<QuerySearchResult> searchQueries(DayQueryPlan plan) {
        if (plan.queries() == null || plan.queries().isEmpty()) {
            return List.of();
        }
        long start = System.currentTimeMillis();
        Semaphore semaphore = new Semaphore(Math.max(1, dayQueryParallelism));
        List<QuerySearchResult> results =
                plan.queries().parallelStream()
                        .map(
                                query ->
                                        new QuerySearchResult(
                                                query, searchWithLimit(query, categoryFor(query), semaphore)))
                        .toList();
        log.info(
                "节点[day-candidate-prepare]：第{}天 POI 并发查询完成，queryCount={}, parallelism={}, elapsedMs={}, resultCounts={}",
                plan.getDay(),
                plan.queries().size(),
                Math.max(1, dayQueryParallelism),
                System.currentTimeMillis() - start,
                results.stream()
                        .map(
                                result ->
                                        result.query().getType()
                                                + ":"
                                                + result.query().getKeyword()
                                                + "="
                                                + result.candidates().size())
                        .toList());
        return results;
    }

    private List<PoiCandidate> searchWithLimit(QueryItem query, String category, Semaphore semaphore) {
        if (!isSearchableQuery(query)) {
            return List.of();
        }
        boolean acquired = false;
        try {
            semaphore.acquire();
            acquired = true;
            return search(query, category);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn(
                    "节点[day-candidate-prepare]：高德查询限流等待被中断，type={}, city={}, keyword={}",
                    query.getType(),
                    query.getCity(),
                    query.getKeyword());
            return List.of();
        } finally {
            if (acquired) {
                semaphore.release();
            }
        }
    }

    private List<DayDataPackage> rankPackages(
            List<DayDataPackage> dataPackages, List<DayContext> dayContexts) {
        List<DayDataPackage> rankedPackages = new ArrayList<>();
        Map<Integer, DayContext> contextByDay =
                dayContexts.stream()
                        .collect(
                                Collectors.toMap(
                                        DayContext::getDay, Function.identity(), (a, b) -> a));
        for (DayDataPackage dataPackage : dataPackages) {
            DayContext dayContext = findDayContext(contextByDay, dataPackage.getDay());
            rankedPackages.add(
                    new DayDataPackage(
                            dataPackage.getDay(),
                            rank(
                                    dataPackage.scenicCandidates(),
                                    dayContext.skeleton().targetArea()),
                            rank(dataPackage.foodCandidates(), dayContext.skeleton().targetArea()),
                            rank(dataPackage.hotelCandidates(), dayContext.hotelArea()),
                            dataPackage.transportRoutes()));
        }
        log.info("节点[day-candidate-prepare]：完成每天候选数据清洗排序，count={}", rankedPackages.size());
        return rankedPackages;
    }

    private List<PoiCandidate> roundRobin(List<List<PoiCandidate>> batches) {
        List<PoiCandidate> result = new ArrayList<>();
        int maxSize = batches.stream().mapToInt(List::size).max().orElse(0);
        for (int index = 0; index < maxSize; index++) {
            for (List<PoiCandidate> batch : batches) {
                if (index < batch.size()) {
                    result.add(batch.get(index));
                }
            }
        }
        return result;
    }

    private List<PoiCandidate> deduplicate(List<PoiCandidate> candidates) {
        Map<String, PoiCandidate> result = new LinkedHashMap<>();
        for (PoiCandidate candidate : candidates) {
            result.putIfAbsent(dedupKey(candidate), candidate);
        }
        return new ArrayList<>(result.values());
    }

    private List<PoiCandidate> search(QueryItem query, String category) {
        long start = System.currentTimeMillis();
        try {
            List<Poi> pois =
                    amapPoiCacheService.searchText(
                            query.getKeyword(),
                            null,
                            query.getCity(),
                            true,
                            25,
                            1,
                            POI_SHOW_FIELDS,
                            category);
            List<PoiCandidate> candidates =
                    pois.stream()
                    .filter(poi -> matchesKeywordName(query, poi))
                    .filter(
                            poi ->
                                    poi.getId() != null
                                            && poi.getName() != null
                                            && poi.getLocation() != null)
                    .filter(poi -> isUsefulPoi(poi, category))
                    .map(poi -> toCandidate(category, poi))
                    .toList();
            log.info(
                    "节点[day-candidate-prepare]：高德候选过滤完成，type={}, category={}, city={}, keyword={}, rawCount={}, validCount={}, elapsedMs={}",
                    query.getType(),
                    category,
                    query.getCity(),
                    query.getKeyword(),
                    pois.size(),
                    candidates.size(),
                    System.currentTimeMillis() - start);
            return candidates;
        } catch (RuntimeException exception) {
            log.warn(
                    "节点[day-candidate-prepare]：高德查询失败，type={}, city={}, keyword={}, elapsedMs={}, reason={}",
                    query.getType(),
                    query.getCity(),
                    query.getKeyword(),
                    System.currentTimeMillis() - start,
                    exception.getMessage());
            return List.of();
        }
    }

    private String categoryFor(QueryItem query) {
        if (query == null) {
            return "SCENIC";
        }
        if ("NIGHT".equals(query.getType())) {
            return "NIGHT";
        }
        if ("FOOD".equals(query.getType())) {
            return "FOOD";
        }
        return "SCENIC";
    }

    private boolean isScenicQuery(QueryItem query) {
        return "SCENIC".equals(query.getType())
                || "AI_SCENIC".equals(query.getType())
                || "SELF_DRIVE".equals(query.getType());
    }

    private boolean isSearchableQuery(QueryItem query) {
        if (query == null) {
            return false;
        }
        if ("TRANSPORT".equals(query.getType())) {
            return false;
        }
        return query.getKeyword() != null && !query.getKeyword().isBlank();
    }

    private List<PoiCandidate> merge(List<PoiCandidate> primary, List<PoiCandidate> fallback) {
        Map<String, PoiCandidate> merged = new LinkedHashMap<>();
        for (PoiCandidate candidate : primary) {
            merged.putIfAbsent(dedupKey(candidate), candidate);
        }
        for (PoiCandidate candidate : fallback) {
            merged.putIfAbsent(dedupKey(candidate), candidate);
        }
        return merged.values().stream().limit(MAX_DAY_CANDIDATES).toList();
    }

    private List<PoiCandidate> mergeScenicCandidates(
            List<PoiCandidate> primary, List<PoiCandidate> curatedFallback) {
        Map<String, PoiCandidate> merged = new LinkedHashMap<>();
        if (primary != null) {
            for (PoiCandidate candidate : primary) {
                merged.putIfAbsent(dedupKey(candidate), candidate);
            }
        }
        int fallbackLimit = curatedFallbackLimit(merged.size());
        int fallbackCount = 0;
        if (curatedFallback != null) {
            for (PoiCandidate candidate : curatedFallback) {
                if (fallbackCount >= fallbackLimit || merged.size() >= MAX_DAY_CANDIDATES) {
                    break;
                }
                String key = dedupKey(candidate);
                if (!merged.containsKey(key)) {
                    merged.put(key, candidate);
                    fallbackCount++;
                }
            }
        }
        return merged.values().stream().limit(MAX_DAY_CANDIDATES).toList();
    }

    private int curatedFallbackLimit(int primaryCount) {
        if (primaryCount <= 0) {
            return MAX_CURATED_FALLBACK_EMPTY_PRIMARY;
        }
        if (primaryCount < 12) {
            return MAX_CURATED_FALLBACK_LOW_PRIMARY;
        }
        return MAX_CURATED_FALLBACK_NORMAL;
    }

    private List<PoiCandidate> cityCandidates(List<PoiCandidate> candidates, String city) {
        if (candidates == null || candidates.isEmpty() || city == null || city.isBlank()) {
            return candidates == null ? List.of() : candidates;
        }
        List<PoiCandidate> filtered =
                candidates.stream().filter(candidate -> sameCity(candidate, city)).toList();
        return filtered;
    }

    private boolean sameCity(PoiCandidate candidate, String city) {
        String normalizedCity = normalizeCity(city);
        return normalizeCity(candidate.getCity()).equals(normalizedCity)
                || normalizeCity(candidate.getAddress()).contains(normalizedCity)
                || normalizeCity(candidate.getArea()).contains(normalizedCity);
    }

    private String dayCity(List<DayContext> dayContexts, Integer day) {
        return dayContexts.stream()
                .filter(item -> item.getDay().equals(day))
                .map(
                        item ->
                                item.skeleton() == null || item.skeleton().getFocusArea() == null
                                        ? null
                                        : item.skeleton().getFocusArea().getCity())
                .filter(city -> city != null && !city.isBlank())
                .findFirst()
                .orElse(null);
    }

    private String normalizeCity(String value) {
        return value == null ? "" : value.replace("市", "").replaceAll("\\s+", "").trim();
    }

    private PoiCandidate toCandidate(String category, Poi poi) {
        return new PoiCandidate(
                category,
                poi.getName(),
                poi.getAddress(),
                firstNonBlank(poi.getAdname(), poi.getCityname()),
                poi.getCityname(),
                poi.getLocation(),
                "AMAP",
                poi.getId(),
                null,
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

    private boolean isUsefulPoi(Poi poi, String category) {
        String name = poi.getName();
        String type = poi.getType() == null ? "" : poi.getType();
        if (isBadPoi(name, type)) {
            return false;
        }
        if ("SCENIC".equals(category) || "NIGHT".equals(category)) {
            return !isStoreLikePoi(name, poi);
        }
        return true;
    }

    private boolean matchesKeywordName(QueryItem query, Poi poi) {
        if (!"AI_SCENIC".equals(query.getType())) {
            return true;
        }
        String place = normalizeSearchName(query.getKeyword(), query.getCity());
        String name = normalizeSearchName(poi.getName(), null);
        if (place.isBlank() || name.isBlank()) {
            return true;
        }
        return name.contains(place) || place.contains(name);
    }

    private boolean isStoreLikePoi(String name, Poi poi) {
        String type = poi.getType() == null ? "" : poi.getType();
        return name.contains("餐厅")
                || name.contains("饭店")
                || name.contains("菜馆")
                || name.contains("小吃")
                || name.contains("美食")
                || name.contains("酒店")
                || name.contains("宾馆")
                || name.contains("商场")
                || name.contains("超市")
                || name.contains("便利店")
                || name.matches(".*[（(].*店[）)].*")
                || type.contains("餐饮")
                || type.contains("购物")
                || type.contains("住宿");
    }

    private boolean isBadPoi(String name, String type) {
        String text = (name == null ? "" : name) + " " + (type == null ? "" : type);
        return containsAny(
                text, "停车场", "停车", "游客中心", "服务中心", "咨询中心", "管理处", "管理局", "管委会", "综合执法", "售票", "票务", "入口", "出口", "出入口",
                "卫生间", "公共厕所", "厕所", "洗手间", "公交站", "地铁站", "加油站", "充电站", "公共设施", "生活服务", "道路附属设施",
                "监控室", "值班室", "办公室", "警务室", "派出所", "管理房", "工作站", "收费站", "指挥部", "执勤点", "检查站");
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeSearchName(String value, String city) {
        String result =
                value == null
                        ? ""
                        : value.replaceAll("[\\s·・（）()【】\\[\\]-]", "").replace("市", "").trim();
        if (city != null && !city.isBlank()) {
            result = result.replace(normalizeSearchName(city, null), "");
        }
        return result;
    }

    private String dedupKey(PoiCandidate candidate) {
        return poiIdentityService.dedupKey(candidate);
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

    private List<PoiCandidate> rank(List<PoiCandidate> candidates, String preferredArea) {
        Map<String, PoiCandidate> deduped = new LinkedHashMap<>();
        for (PoiCandidate candidate : candidates) {
            deduped.putIfAbsent(candidate.getName(), candidate);
        }
        return deduped.values().stream()
                .sorted(
                        Comparator.comparing(
                                candidate -> !containsPreferredArea(candidate, preferredArea)))
                .limit(MAX_RANKED_CANDIDATES)
                .toList();
    }

    private boolean containsPreferredArea(PoiCandidate candidate, String preferredArea) {
        return preferredArea != null
                && candidate.getArea() != null
                && preferredArea.contains(candidate.getArea());
    }

    private DayContext findDayContext(Map<Integer, DayContext> contextByDay, Integer day) {
        DayContext dayContext = contextByDay.get(day);
        if (dayContext == null) {
            throw new IllegalStateException("缺少第 " + day + " 天上下文，无法排序候选数据");
        }
        return dayContext;
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private record QuerySearchResult(QueryItem query, List<PoiCandidate> candidates) {}
}
