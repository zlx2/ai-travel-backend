package com.sora.aitravel.workflow.generate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 清洗、去重、筛选和排序每天的工具候选数据。 */
@Slf4j
@Component
public class DayDataRankNode {

    private static final int MAX_RANKED_CANDIDATES = 40;

    public void execute(GenerateWorkflowContext context) {
        List<DayDataPackage> rankedPackages = new ArrayList<>();
        for (DayDataPackage dataPackage : context.getRankedDayDataPackages()) {
            DayContext dayContext = findDayContext(context, dataPackage.getDay());
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
        context.setRankedDayDataPackages(rankedPackages);
        log.info("节点[day-data-rank]：完成每天候选数据清洗排序，count={}", rankedPackages.size());
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

    private DayContext findDayContext(GenerateWorkflowContext context, Integer day) {
        return context.getDayContexts().stream()
                .filter(item -> item.getDay().equals(day))
                .findFirst()
                .orElseThrow();
    }
}
