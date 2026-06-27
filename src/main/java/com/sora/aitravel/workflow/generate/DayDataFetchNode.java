package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.dto.model.poi.Poi;
import com.sora.aitravel.service.AmapPoiCacheService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 执行每天的高德查询计划，并用城市候选池补充结果。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DayDataFetchNode {

    private static final int MAX_DAY_CANDIDATES = 50;
    private static final String POI_SHOW_FIELDS = "business,navi,photos";

    private final AmapPoiCacheService amapPoiCacheService;

    public void execute(GenerateWorkflowContext context) {
        List<DayDataPackage> packages = new ArrayList<>();
        for (DayQueryPlan plan : context.getDayQueryPlans()) {
            List<List<PoiCandidate>> scenicBatches = new ArrayList<>();
            List<PoiCandidate> night = new ArrayList<>();
            List<PoiCandidate> food = new ArrayList<>();
            for (QueryItem query : plan.queries()) {
                if ("SCENIC".equals(query.getType())) {
                    scenicBatches.add(search(query, "SCENIC"));
                } else if ("NIGHT".equals(query.getType())) {
                    night.addAll(search(query, "NIGHT"));
                } else if ("FOOD".equals(query.getType())) {
                    food.addAll(search(query, "FOOD"));
                }
            }
            List<PoiCandidate> scenic = new ArrayList<>();
            scenic.addAll(roundRobin(scenicBatches).stream().limit(42).toList());
            scenic.addAll(deduplicate(night).stream().limit(8).toList());
            packages.add(
                    new DayDataPackage(
                            plan.getDay(),
                            merge(scenic, context.getCityProfile().scenicCandidates()),
                            merge(food, context.getCityProfile().foodCandidates()),
                            context.getCityProfile().hotelCandidates(),
                            List.of()));
        }
        context.setRankedDayDataPackages(packages);
        log.info(
                "节点[day-data-fetch]：已执行每天 POI 查询，days={}, scenicCounts={}",
                packages.size(),
                packages.stream().map(item -> item.scenicCandidates().size()).toList());
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
            return pois.stream()
                    .filter(
                            poi ->
                                    poi.getId() != null
                                            && poi.getName() != null
                                            && poi.getLocation() != null)
                    .filter(poi -> isUsefulPoi(poi, category))
                    .map(poi -> toCandidate(category, poi))
                    .toList();
        } catch (RuntimeException exception) {
            log.warn(
                    "节点[day-data-fetch]：高德查询失败，dayKeyword={}, reason={}",
                    query.getKeyword(),
                    exception.getMessage());
            return List.of();
        }
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

    private PoiCandidate toCandidate(String category, Poi poi) {
        return new PoiCandidate(
                category,
                poi.getName(),
                poi.getAddress(),
                firstNonBlank(poi.getAdname(), poi.getCityname()),
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
        if (name.contains("停车场")
                || name.contains("游客中心")
                || name.contains("入口")
                || name.contains("售票")
                || name.contains("卫生间")
                || name.contains("公交站")) {
            return false;
        }
        if ("SCENIC".equals(category) || "NIGHT".equals(category)) {
            return !isStoreLikePoi(name, poi);
        }
        return true;
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

    private String dedupKey(PoiCandidate candidate) {
        String name = candidate.getName() == null ? "" : candidate.getName();
        return name.replaceAll("[（(].*?[）)]", "")
                .replaceAll("[-—·].*$", "")
                .replace("景区", "")
                .replace("风景区", "")
                .replace("步行街", "")
                .replaceAll("\\s+", "")
                .trim();
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
