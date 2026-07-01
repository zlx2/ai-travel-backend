package com.sora.aitravel.service.impl;

import com.sora.aitravel.model.RouteAnchor;
import com.sora.aitravel.service.RouteOrderOptimizer;
import com.sora.aitravel.service.route.RouteMatrix;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Optimizes route order with fixed start/end and soft meal-position constraints. */
@Component
public class RouteOrderOptimizerImpl implements RouteOrderOptimizer {
    private static final int MAX_EXACT_MIDDLE_POINTS = 12;
    private static final int MEAL_POSITION_PENALTY_SECONDS = 20 * 60;

    @Override
    public List<RouteAnchor> optimize(
            RouteAnchor start, List<RouteAnchor> middle, RouteAnchor end, RouteMatrix matrix) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Route start and end are required");
        }
        List<RouteAnchor> candidates =
                middle == null
                        ? List.of()
                        : middle.stream()
                                .filter(anchor -> anchor != null && anchor.hasLocation())
                                .toList();
        if (candidates.isEmpty()) {
            return List.of(start, end);
        }
        if (candidates.size() <= MAX_EXACT_MIDDLE_POINTS) {
            return exactOptimize(start, candidates, end, matrix);
        }
        return twoOpt(start, nearestNeighbor(start, candidates, end, matrix), end, matrix);
    }

    private List<RouteAnchor> exactOptimize(
            RouteAnchor start, List<RouteAnchor> middle, RouteAnchor end, RouteMatrix matrix) {
        int n = middle.size();
        int states = 1 << n;
        Map<State, Score> dp = new HashMap<>();
        for (int index = 0; index < n; index++) {
            RouteAnchor anchor = middle.get(index);
            int mask = 1 << index;
            int cost =
                    matrix.durationSeconds(start.getId(), anchor.getId())
                            + positionPenalty(anchor, 1, n);
            dp.put(new State(mask, index), new Score(cost, -1));
        }
        for (int mask = 1; mask < states; mask++) {
            int position = Integer.bitCount(mask);
            for (int last = 0; last < n; last++) {
                State state = new State(mask, last);
                Score current = dp.get(state);
                if (current == null) {
                    continue;
                }
                for (int next = 0; next < n; next++) {
                    if ((mask & (1 << next)) != 0) {
                        continue;
                    }
                    RouteAnchor nextAnchor = middle.get(next);
                    int nextMask = mask | (1 << next);
                    int cost =
                            current.cost()
                                    + matrix.durationSeconds(
                                            middle.get(last).getId(), nextAnchor.getId())
                                    + positionPenalty(nextAnchor, position + 1, n);
                    State nextState = new State(nextMask, next);
                    Score existing = dp.get(nextState);
                    if (existing == null || cost < existing.cost()) {
                        dp.put(nextState, new Score(cost, last));
                    }
                }
            }
        }

        int fullMask = states - 1;
        State bestState = null;
        int bestCost = Integer.MAX_VALUE;
        for (int last = 0; last < n; last++) {
            Score score = dp.get(new State(fullMask, last));
            if (score == null) {
                continue;
            }
            int cost = score.cost() + matrix.durationSeconds(middle.get(last).getId(), end.getId());
            if (cost < bestCost) {
                bestCost = cost;
                bestState = new State(fullMask, last);
            }
        }
        if (bestState == null) {
            return nearestNeighbor(start, middle, end, matrix);
        }

        List<RouteAnchor> orderedMiddle = new ArrayList<>();
        State cursor = bestState;
        while (cursor.lastIndex() >= 0) {
            orderedMiddle.add(middle.get(cursor.lastIndex()));
            Score score = dp.get(cursor);
            if (score == null || score.previousLastIndex() < 0) {
                break;
            }
            int previousMask = cursor.mask() ^ (1 << cursor.lastIndex());
            cursor = new State(previousMask, score.previousLastIndex());
        }
        java.util.Collections.reverse(orderedMiddle);
        return withStartEnd(start, orderedMiddle, end);
    }

    private List<RouteAnchor> nearestNeighbor(
            RouteAnchor start, List<RouteAnchor> middle, RouteAnchor end, RouteMatrix matrix) {
        List<RouteAnchor> remaining = new ArrayList<>(middle);
        List<RouteAnchor> ordered = new ArrayList<>();
        RouteAnchor cursor = start;
        int position = 1;
        while (!remaining.isEmpty()) {
            RouteAnchor current = cursor;
            int currentPosition = position;
            RouteAnchor next =
                    remaining.stream()
                            .min(
                                    Comparator.comparingInt(
                                            anchor ->
                                                    matrix.durationSeconds(
                                                                    current.getId(), anchor.getId())
                                                            + positionPenalty(
                                                                    anchor,
                                                                    currentPosition,
                                                                    middle.size())))
                            .orElseThrow();
            ordered.add(next);
            remaining.remove(next);
            cursor = next;
            position++;
        }
        return withStartEnd(start, ordered, end);
    }

    private List<RouteAnchor> twoOpt(
            RouteAnchor start, List<RouteAnchor> route, RouteAnchor end, RouteMatrix matrix) {
        List<RouteAnchor> middle = new ArrayList<>(route.subList(1, route.size() - 1));
        boolean improved = true;
        while (improved) {
            improved = false;
            for (int i = 0; i < middle.size() - 1; i++) {
                for (int j = i + 1; j < middle.size(); j++) {
                    List<RouteAnchor> candidate = new ArrayList<>(middle);
                    java.util.Collections.reverse(candidate.subList(i, j + 1));
                    if (routeCost(withStartEnd(start, candidate, end), matrix)
                            < routeCost(withStartEnd(start, middle, end), matrix)) {
                        middle = candidate;
                        improved = true;
                    }
                }
            }
        }
        return withStartEnd(start, middle, end);
    }

    private int routeCost(List<RouteAnchor> route, RouteMatrix matrix) {
        int cost = 0;
        for (int index = 0; index < route.size() - 1; index++) {
            cost += matrix.durationSeconds(route.get(index).getId(), route.get(index + 1).getId());
        }
        return cost;
    }

    private List<RouteAnchor> withStartEnd(
            RouteAnchor start, List<RouteAnchor> middle, RouteAnchor end) {
        List<RouteAnchor> result = new ArrayList<>();
        result.add(start);
        result.addAll(middle);
        result.add(end);
        return result;
    }

    private int positionPenalty(RouteAnchor anchor, int position, int middleCount) {
        String type = anchor.getType() == null ? "" : anchor.getType();
        double ratio = middleCount <= 1 ? 0.5 : (double) position / (middleCount + 1);
        if ("LUNCH_AREA".equals(type) && (ratio < 0.25 || ratio > 0.65)) {
            return MEAL_POSITION_PENALTY_SECONDS;
        }
        if ("DINNER_AREA".equals(type) && ratio < 0.55) {
            return MEAL_POSITION_PENALTY_SECONDS;
        }
        return 0;
    }

    private static final class State {
        private final int mask;
        private final int lastIndex;

        private State(int mask, int lastIndex) {
            this.mask = mask;
            this.lastIndex = lastIndex;
        }

        private int mask() {
            return mask;
        }

        private int lastIndex() {
            return lastIndex;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof State state)) {
                return false;
            }
            return mask == state.mask && lastIndex == state.lastIndex;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(mask, lastIndex);
        }
    }

    private static final class Score {
        private final int cost;
        private final int previousLastIndex;

        private Score(int cost, int previousLastIndex) {
            this.cost = cost;
            this.previousLastIndex = previousLastIndex;
        }

        private int cost() {
            return cost;
        }

        private int previousLastIndex() {
            return previousLastIndex;
        }
    }
}
