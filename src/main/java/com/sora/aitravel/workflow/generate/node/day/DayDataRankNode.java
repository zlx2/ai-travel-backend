package com.sora.aitravel.workflow.generate.node.day;

import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.DAY_CONTEXTS;
import static com.sora.aitravel.workflow.generate.state.TripGraphStateKeys.RANKED_DAY_DATA_PACKAGES;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.sora.aitravel.model.trip.generate.DayContext;
import com.sora.aitravel.model.trip.generate.DayDataPackage;
import com.sora.aitravel.model.trip.generate.PoiCandidate;
import com.sora.aitravel.workflow.generate.state.TripGraphStateCodec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 清洗、去重、筛选和排序每天的工具候选数据。 */
@Slf4j
@Component
public class DayDataRankNode {

    private static final int MAX_RANKED_CANDIDATES = 40;

    public Map<String, Object> execute(OverAllState state) {
        List<DayDataPackage> rankedPackages =
                rankPackages(
                        TripGraphStateCodec.optionalList(
                                state, RANKED_DAY_DATA_PACKAGES, DayDataPackage.class),
                        TripGraphStateCodec.optionalList(state, DAY_CONTEXTS, DayContext.class));
        return TripGraphStateCodec.patch(RANKED_DAY_DATA_PACKAGES, rankedPackages);
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
        log.info("节点[day-data-rank]：完成每天候选数据清洗排序，count={}", rankedPackages.size());
        return rankedPackages;
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
}
