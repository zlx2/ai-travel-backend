package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.dto.model.AmapApiResp;
import com.sora.aitravel.dto.model.geo.GeoCode;
import com.sora.aitravel.dto.model.route.Path;
import com.sora.aitravel.dto.model.route.Route;
import com.sora.aitravel.dto.model.route.Step;
import com.sora.aitravel.service.AmapApiService;
import com.sora.aitravel.tools.AmapGeoTool;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 路线推荐数据获取节点。
 * 使用 AmapGeoTool 进行地理编码转换，确保经纬度保留小数点后6位。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RouteRecommendNode {

    private final AmapApiService amapApiService;
    private final AmapGeoTool amapGeoTool;

    public void execute(GenerateWorkflowContext context) {
        List<DayDataPackage> packages = new ArrayList<>();

        if (context.getDayQueryPlans() == null || context.getDayQueryPlans().isEmpty()) {
            log.warn("节点[route-recommend]: 未找到每日查询计划，跳过");
            return;
        }

        for (DayQueryPlan plan : context.getDayQueryPlans()) {
            List<TransportRoute> routes = new ArrayList<>();

            for (QueryItem query : plan.queries()) {
                if ("TRANSPORT".equals(query.type())) {
                    TransportRoute route = queryRoute(plan.day(), query, context);
                    if (route != null) {
                        routes.add(route);
                    }
                }
            }

            DayDataPackage existing = findDayPackage(context, plan.day());
            if (existing != null) {
                packages.add(new DayDataPackage(
                        plan.day(),
                        existing.scenicCandidates(),
                        existing.foodCandidates(),
                        existing.hotelCandidates(),
                        routes.isEmpty() ? existing.transportRoutes() : routes
                ));
            } else {
                packages.add(new DayDataPackage(
                        plan.day(),
                        context.getCityProfile().scenicCandidates(),
                        context.getCityProfile().foodCandidates(),
                        context.getCityProfile().hotelCandidates(),
                        routes
                ));
            }
        }

        context.setRankedDayDataPackages(packages);
    }

    private TransportRoute queryRoute(Integer day, QueryItem query, GenerateWorkflowContext context) {
        String city = getCityFromContext(query, context);
        String fromLatLng = resolveLatLng(query.from(), city, context);
        String toLatLng = resolveLatLng(query.to(), city, context);

        if (fromLatLng == null || toLatLng == null) {
            log.warn("节点[route-recommend]: 第{}天 - 无法解析经纬度(from={}, to={}), 使用模拟路线",
                    day, query.from(), query.to());
            return createSimulatedRoute(query);
        }

        try {
            log.info("节点[route-recommend]: 第{}天 - 查询路线 from={} to={}", day, fromLatLng, toLatLng);

            AmapApiResp<Route> response = amapApiService.drivingRoute(fromLatLng, toLatLng);

            if (isUsable(response)) {
                log.info("节点[route-recommend]: 第{}天 - 使用高德真实路线数据", day);
                return convertToTransportRoute(response.getData(), "DRIVING", query);
            }

            log.warn("节点[route-recommend]: 第{}天 - 真实路线API返回无效数据", day);
            return createSimulatedRoute(query);

        } catch (RuntimeException exception) {
            log.warn("节点[route-recommend]: 第{}天 - 路线API调用失败: {}", day, exception.getMessage());
            return createSimulatedRoute(query);
        }
    }

    private String getCityFromContext(QueryItem query, GenerateWorkflowContext context) {
        if (query.getCity() != null && !query.getCity().isBlank()) {
            return query.getCity();
        }
        if (context.getCityProfile() != null
                && context.getCityProfile().destination() != null
                && !context.getCityProfile().destination().isBlank()) {
            return context.getCityProfile().destination();
        }
        if (context.getRequirement() != null
                && context.getRequirement().getDestination() != null
                && !context.getRequirement().getDestination().isBlank()) {
            return context.getRequirement().getDestination();
        }
        return null;
    }

    private String resolveLatLng(String location, String city, GenerateWorkflowContext context) {
        if (location == null || location.isBlank()) {
            location = getAlternativeLocation(context);
            if (location == null || location.isBlank()) {
                return null;
            }
        }

        if (location.matches("^-?\\d+\\.?\\d*,\\s*-?\\d+\\.?\\d*$")) {
            return formatLatLng(location);
        }

        try {
            log.debug("地理编码: location={}, city={}", location, city);
            AmapApiResp<List<GeoCode>> resp = amapGeoTool.geoCode(location, city);

            if (resp != null && resp.isSuccess() && resp.getData() != null && !resp.getData().isEmpty()) {
                GeoCode geo = resp.getData().getFirst();
                String latLng = geo.getLocation();
                log.debug("地理编码成功: {} -> {}", location, latLng);
                return formatLatLng(latLng);
            }
        } catch (Exception e) {
            log.warn("地理编码失败 location={}, city={}: {}", location, city, e.getMessage());
        }

        String candidateLatLng = getLatLngFromCandidates(location, context);
        if (candidateLatLng != null) {
            return formatLatLng(candidateLatLng);
        }

        return null;
    }

    private String getAlternativeLocation(GenerateWorkflowContext context) {
        if (context.getCityProfile() != null && context.getCityProfile().destination() != null) {
            return context.getCityProfile().destination();
        }
        if (context.getRequirement() != null && context.getRequirement().getDestination() != null) {
            return context.getRequirement().getDestination();
        }
        return null;
    }

    private String getLatLngFromCandidates(String locationName, GenerateWorkflowContext context) {
        if (context.getCityProfile() == null || locationName == null) {
            return null;
        }

        if (context.getCityProfile().hotelCandidates() != null) {
            for (PoiCandidate candidate : context.getCityProfile().hotelCandidates()) {
                if (candidate.getName() != null && candidate.getName().contains(locationName)) {
                    return candidate.getLocation();
                }
            }
        }

        if (context.getCityProfile().scenicCandidates() != null) {
            for (PoiCandidate candidate : context.getCityProfile().scenicCandidates()) {
                if (candidate.getName() != null && candidate.getName().contains(locationName)) {
                    return candidate.getLocation();
                }
            }
        }

        if (context.getCityProfile().foodCandidates() != null) {
            for (PoiCandidate candidate : context.getCityProfile().foodCandidates()) {
                if (candidate.getName() != null && candidate.getName().contains(locationName)) {
                    return candidate.getLocation();
                }
            }
        }

        return null;
    }

    private String formatLatLng(String latLng) {
        if (latLng == null || !latLng.contains(",")) {
            return latLng;
        }

        try {
            String[] parts = latLng.split(",");
            if (parts.length >= 2) {
                BigDecimal lon = new BigDecimal(parts[0].trim()).setScale(6, RoundingMode.HALF_UP);
                BigDecimal lat = new BigDecimal(parts[1].trim()).setScale(6, RoundingMode.HALF_UP);
                return lon + "," + lat;
            }
        } catch (NumberFormatException e) {
            log.warn("经纬度格式化失败: {}", latLng);
        }

        return latLng;
    }

    private boolean isUsable(AmapApiResp<Route> response) {
        return response != null
                && response.isSuccess()
                && response.getData() != null
                && response.getData().getPaths() != null
                && !response.getData().getPaths().isEmpty();
    }

    /**
     * 将高德Route响应转换为TransportRoute。
     * 关键改进：从Step的cost.duration累加计算总耗时
     */
    private TransportRoute convertToTransportRoute(Route route, String mode, QueryItem query) {
        if (route.getPaths() == null || route.getPaths().isEmpty()) {
            return createSimulatedRoute(query);
        }

        // 获取第一条路线方案
        Path path = route.getPaths().getFirst();

        // 计算总耗时（累加所有Step的cost.duration）
        int totalDurationSeconds = calculateTotalDuration(path);

        // 获取总距离
        String distance = path.getDistance();

        // 获取出租车费用
        String taxiCost = route.getTaxiCost();

        // 获取红绿灯数量
        int trafficLights = calculateTrafficLights(path);

        log.info("路线计算结果 - 距离: {}米, 耗时: {}秒, 出租车费用: {}元, 红绿灯: {}个",
                distance, totalDurationSeconds, taxiCost, trafficLights);

        return new TransportRoute(
                formatLatLng(route.getOrigin()),
                formatLatLng(route.getDestination()),
                mode,
                formatDuration(totalDurationSeconds),
                formatDistance(distance),
                "AMAP_API",
                false
        );
    }

    /**
     * 从Path中计算总耗时（累加所有Step的cost.duration）
     */
    private int calculateTotalDuration(Path path) {
        int totalSeconds = 0;

        if (path.getSteps() != null) {
            for (Step step : path.getSteps()) {
                if (step.getCost() != null && step.getCost().getDuration() != null) {
                    try {
                        totalSeconds += Integer.parseInt(step.getCost().getDuration());
                    } catch (NumberFormatException e) {
                        log.warn("解析Step耗时失败: {}", step.getCost().getDuration());
                    }
                }
            }
        }

        return totalSeconds;
    }

    /**
     * 计算红绿灯总数
     */
    private int calculateTrafficLights(Path path) {
        int totalLights = 0;

        if (path.getSteps() != null) {
            for (Step step : path.getSteps()) {
                if (step.getCost() != null && step.getCost().getTraffic_lights() != null) {
                    try {
                        totalLights += Integer.parseInt(step.getCost().getTraffic_lights());
                    } catch (NumberFormatException e) {
                        log.warn("解析红绿灯数量失败: {}", step.getCost().getTraffic_lights());
                    }
                }
            }
        }

        return totalLights;
    }

    /**
     * 将秒数格式化为可读文本。
     */
    private String formatDuration(int totalSeconds) {
        if (totalSeconds <= 0) {
            return "约30分钟";
        }

        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;

        if (hours > 0) {
            return String.format("约%d小时%d分钟", hours, minutes);
        }
        return "约" + minutes + "分钟";
    }

    /**
     * 将米数格式化为可读文本。
     */
    private String formatDistance(String distanceMeters) {
        if (distanceMeters == null) {
            return "约8公里";
        }

        try {
            int meters = Integer.parseInt(distanceMeters);
            if (meters >= 1000) {
                return String.format("约%.1f公里", meters / 1000.0);
            }
            return "约" + meters + "米";
        } catch (NumberFormatException e) {
            return "约8公里";
        }
    }

    private TransportRoute createSimulatedRoute(QueryItem query) {
        return new TransportRoute(
                query.from(),
                query.to(),
                "TAXI",
                "约30分钟",
                "约8公里",
                "SIMULATED_AMAP",
                true
        );
    }

    private DayDataPackage findDayPackage(GenerateWorkflowContext context, Integer day) {
        if (context.getRankedDayDataPackages() == null) {
            return null;
        }
        return context.getRankedDayDataPackages().stream()
                .filter(pkg -> pkg.day().equals(day))
                .findFirst()
                .orElse(null);
    }
}