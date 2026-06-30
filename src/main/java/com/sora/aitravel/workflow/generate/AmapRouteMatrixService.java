package com.sora.aitravel.workflow.generate;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.config.AmapProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Builds driving-cost matrices through AMap distance API and caches pair metrics. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AmapRouteMatrixService implements RouteMatrixService {
    private static final String SOURCE_AMAP = "AMAP_DISTANCE";
    private static final int MAX_ORIGINS_PER_REQUEST = 8;
    private static final int MAX_RETRY = 4;
    private static final long MIN_REQUEST_INTERVAL_MS = 280L;
    private static final Object RATE_LIMIT_LOCK = new Object();
    private static long lastRequestAt;

    private final AmapProperties amapProperties;
    private final Map<String, RouteLegMetric> metricCache = new ConcurrentHashMap<>();

    @Override
    public RouteMatrix buildDrivingMatrix(List<RouteAnchor> anchors) {
        List<RouteAnchor> usable = anchors == null ? List.of() : anchors.stream()
                .filter(RouteAnchor::hasLocation)
                .toList();
        if (usable.size() < 2) {
            return new RouteMatrix(usable);
        }
        RouteMatrix matrix = new RouteMatrix(usable);
        for (RouteAnchor destination : usable) {
            List<RouteAnchor> origins = usable.stream()
                    .filter(origin -> !origin.getId().equals(destination.getId()))
                    .toList();
            for (List<RouteAnchor> chunk : chunks(origins, MAX_ORIGINS_PER_REQUEST)) {
                List<RouteAnchor> missing = new ArrayList<>();
                for (RouteAnchor origin : chunk) {
                    RouteLegMetric cached = metricCache.get(cacheKey(origin, destination));
                    if (cached == null) {
                        missing.add(origin);
                    } else {
                        matrix.put(cached);
                    }
                }
                if (missing.isEmpty()) {
                    continue;
                }
                List<RouteLegMetric> fetched = fetchDistance(destination, missing);
                fetched.forEach(metric -> {
                    metricCache.put(cacheKey(metric.getFromId(), metric.getToId()), metric);
                    matrix.put(metric);
                });
            }
        }
        return matrix;
    }

    @Override
    public List<RouteLegMetric> buildDrivingRouteMetrics(List<RouteAnchor> orderedAnchors) {
        List<RouteAnchor> route =
                orderedAnchors == null ? List.of() : orderedAnchors.stream()
                        .filter(RouteAnchor::hasLocation)
                        .toList();
        if (route.size() < 2) {
            return List.of();
        }
        List<RouteLegMetric> result = new ArrayList<>();
        Map<String, List<RouteAnchor>> missingByDestination = new java.util.LinkedHashMap<>();
        for (int index = 0; index < route.size() - 1; index++) {
            RouteAnchor origin = route.get(index);
            RouteAnchor destination = route.get(index + 1);
            RouteLegMetric cached = metricCache.get(cacheKey(origin, destination));
            if (cached != null) {
                result.add(cached);
                continue;
            }
            missingByDestination
                    .computeIfAbsent(destination.getId(), key -> new ArrayList<>())
                    .add(origin);
        }
        Map<String, RouteAnchor> byId = route.stream()
                .collect(java.util.stream.Collectors.toMap(RouteAnchor::getId, anchor -> anchor, (a, b) -> a));
        for (Map.Entry<String, List<RouteAnchor>> entry : missingByDestination.entrySet()) {
            RouteAnchor destination = byId.get(entry.getKey());
            for (List<RouteAnchor> chunk : chunks(entry.getValue(), MAX_ORIGINS_PER_REQUEST)) {
                List<RouteLegMetric> fetched = fetchDistance(destination, chunk);
                fetched.forEach(metric -> metricCache.put(cacheKey(metric.getFromId(), metric.getToId()), metric));
            }
        }
        result.clear();
        for (int index = 0; index < route.size() - 1; index++) {
            RouteLegMetric metric = metricCache.get(cacheKey(route.get(index), route.get(index + 1)));
            if (metric != null) {
                result.add(metric);
            }
        }
        return result;
    }

    private List<RouteLegMetric> fetchDistance(RouteAnchor destination, List<RouteAnchor> origins) {
        JSONObject json = requestDistance(destination, origins);
        JSONArray results = json.getJSONArray("results");
        if (results == null || results.size() != origins.size()) {
            throw new BusinessException(
                    ErrorCode.AI_GENERATE_ERROR,
                    "高德距离矩阵返回数量异常，destination=" + destination.getTitle());
        }
        List<RouteLegMetric> metrics = new ArrayList<>();
        for (int index = 0; index < origins.size(); index++) {
            RouteAnchor origin = origins.get(index);
            JSONObject item = results.getJSONObject(index);
            metrics.add(RouteLegMetric.builder()
                    .fromId(origin.getId())
                    .toId(destination.getId())
                    .distanceMeters(parseInt(item.getStr("distance")))
                    .durationSeconds(parseInt(item.getStr("duration")))
                    .source(SOURCE_AMAP)
                    .build());
        }
        return metrics;
    }

    private JSONObject requestDistance(RouteAnchor destination, List<RouteAnchor> origins) {
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            throttle();
            String response =
                    HttpRequest.get(baseUrl() + "/v3/distance")
                            .timeout((int) timeout().toMillis())
                            .form("key", apiKey())
                            .form(
                                    "origins",
                                    String.join(
                                            "|", origins.stream().map(RouteAnchor::location).toList()))
                            .form("destination", destination.location())
                            .form("type", "1")
                            .execute()
                            .body();
            JSONObject json = JSONUtil.parseObj(response);
            if ("1".equals(json.getStr("status"))) {
                return json;
            }
            String info = json.getStr("info", "unknown");
            String infocode = json.getStr("infocode", "");
            log.warn(
                    "高德距离矩阵请求失败，attempt={}, info={}, infocode={}, destination={}, origins={}",
                    attempt,
                    info,
                    infocode,
                    destination.getTitle(),
                    origins.size());
            if (!isQpsLimit(info, infocode) || attempt == MAX_RETRY) {
                throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "高德距离矩阵请求失败：" + info);
            }
            sleepQuietly(700L * attempt);
        }
        throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "高德距离矩阵请求失败：unknown");
    }

    private void throttle() {
        synchronized (RATE_LIMIT_LOCK) {
            long now = System.currentTimeMillis();
            long wait = MIN_REQUEST_INTERVAL_MS - (now - lastRequestAt);
            if (wait > 0) {
                sleepQuietly(wait);
            }
            lastRequestAt = System.currentTimeMillis();
        }
    }

    private boolean isQpsLimit(String info, String infocode) {
        String text = (info == null ? "" : info) + " " + (infocode == null ? "" : infocode);
        return text.contains("CUQPS") || text.contains("QPS") || text.contains("10020");
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.AI_GENERATE_ERROR, "高德距离矩阵请求被中断");
        }
    }

    private String apiKey() {
        String key = amapProperties.getApiKey();
        if (StrUtil.isBlank(key)) {
            key = System.getenv("AMAP_API_KEY");
        }
        if (StrUtil.isBlank(key)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "未配置高德 WebService Key");
        }
        return key;
    }

    private String baseUrl() {
        return StrUtil.blankToDefault(amapProperties.getBaseUrl(), "https://restapi.amap.com");
    }

    private Duration timeout() {
        return amapProperties.getTimeout() == null ? Duration.ofSeconds(10) : amapProperties.getTimeout();
    }

    private int parseInt(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return (int) Math.round(Double.parseDouble(value));
    }

    private String cacheKey(RouteAnchor origin, RouteAnchor destination) {
        return cacheKey(origin.getId(), destination.getId());
    }

    private String cacheKey(String originId, String destinationId) {
        return "drive:" + originId + ":" + destinationId;
    }

    private List<List<RouteAnchor>> chunks(List<RouteAnchor> values, int size) {
        List<List<RouteAnchor>> chunks = new ArrayList<>();
        for (int index = 0; index < values.size(); index += size) {
            chunks.add(values.subList(index, Math.min(values.size(), index + size)));
        }
        return chunks;
    }
}
