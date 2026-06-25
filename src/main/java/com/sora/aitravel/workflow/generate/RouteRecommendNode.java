package com.sora.aitravel.workflow.generate;

import com.sora.aitravel.dto.model.AmapApiResp;
import com.sora.aitravel.dto.model.geo.GeoCode;
import com.sora.aitravel.dto.model.route.*;
import com.sora.aitravel.dto.model.TravelRequirementDTO;
import com.sora.aitravel.tools.AmapGeoTool;
import com.sora.aitravel.tools.AmapRouteTool;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 路线推荐数据获取节点。
 * 负责为旅行计划中的交通任务(TRANSPORT)查询实际路线信息，
 * 根据用户选择的交通方式调用不同的高德地图API获取路线数据，
 * 支持驾车、步行、公交地铁换乘等多种方式，失败时生成模拟路线作为降级方案。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RouteRecommendNode {

    /**
     * 高德地理编码工具
     */
    private final AmapGeoTool amapGeoTool;
    /**
     * 高德路线规划工具（支持多种交通方式）
     */
    private final AmapRouteTool amapRouteTool;

    /**
     * 执行路线推荐主流程。
     * 遍历每日查询计划，对每个TRANSPORT类型的任务查询路线，并将结果封装到DayDataPackage中。
     *
     * @param context 工作流上下文，包含每日查询计划、城市信息等
     */
    public void execute(GenerateWorkflowContext context) {
        List<DayDataPackage> packages = new ArrayList<>();

        // 未查询到每日计划，不进行路径规划
        if (context.getDayQueryPlans() == null || context.getDayQueryPlans().isEmpty()) {
            log.warn("节点[route-recommend]: 未找到每日查询计划，跳过");
            return;
        }

        // 遍历每日查询计划，对所有TRANSPORT任务进行路径规划
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

            // 查找已存在的DayDataPackage，存在则更新路线，不存在则创建新的
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

        // 更新上下文的每日数据包列表
        context.setRankedDayDataPackages(packages);
    }

    /**
     * 查询单条路线信息。
     * 根据用户选择的交通方式调用不同的高德地图API获取路线，失败时返回模拟路线。
     *
     * @param day     第几天的计划
     * @param query   查询项，包含起点(from)和终点(to)
     * @param context 工作流上下文
     * @return 交通路线信息
     */
    private TransportRoute queryRoute(Integer day, QueryItem query, GenerateWorkflowContext context) {
        // 获取城市信息
        String city = getCityFromContext(query, context);
        // 解析起点和终点经纬度
        String fromLatLng = resolveLatLng(query.from(), city, context);
        String toLatLng = resolveLatLng(query.to(), city, context);

        // 经纬度解析失败，使用模拟路线
        if (fromLatLng == null || toLatLng == null) {
            log.warn("节点[route-recommend]: 第{}天 - 无法解析经纬度(from={}, to={}), 使用模拟路线",
                    day, query.from(), query.to());
            return createSimulatedRoute(query);
        }

        // 获取用户选择的交通方式
        String transportMode = getTransportMode(context);
        log.info("节点[route-recommend]: 第{}天 - 用户交通方式: {}, 查询路线 from={} to={}",
                day, transportMode, fromLatLng, toLatLng);

        try {
            AmapApiResp<Route> response = callRouteApi(transportMode, fromLatLng, toLatLng, city);

            if (isUsable(response)) {
                log.info("节点[route-recommend]: 第{}天 - 使用高德真实路线数据", day);
                return convertToTransportRoute(response.getData(), transportMode, query);
            }

            // 公交路线可能使用transits字段
            if (isTransitUsable(response)) {
                log.info("节点[route-recommend]: 第{}天 - 使用公交换乘路线数据", day);
                return convertTransitToTransportRoute(response.getData(), query);
            }

            log.warn("节点[route-recommend]: 第{}天 - 真实路线API返回无效数据", day);
            return createSimulatedRoute(query);

        } catch (RuntimeException exception) {
            log.warn("节点[route-recommend]: 第{}天 - 路线API调用失败: {}", day, exception.getMessage());
            return createSimulatedRoute(query);
        }
    }

    /**
     * 从上下文获取用户选择的交通方式。
     * 如果未选择，则默认使用DRIVING。
     *
     * @param context 工作流上下文
     * @return 交通方式
     */
    private String getTransportMode(GenerateWorkflowContext context) {
        TravelRequirementDTO requirement = context.getRequirement();
        if (requirement != null && requirement.getTransportMode() != null && !requirement.getTransportMode().isBlank()) {
            String mode = requirement.getTransportMode();
            // 映射到实际使用的路线类型
            return "PUBLIC_TRANSIT".equals(mode) ? "TRANSIT" : "DRIVING";
        }
        return "DRIVING";
    }

    /**
     * 根据交通方式调用对应的高德地图API。
     *
     * @param transportMode 交通方式
     * @param fromLatLng    起点经纬度
     * @param toLatLng      终点经纬度
     * @param city          城市名称
     * @return 路线API响应
     */
    private AmapApiResp<Route> callRouteApi(String transportMode, String fromLatLng, String toLatLng, String city) {
        return switch (transportMode) {
            case "WALKING" -> amapRouteTool.walkingRoute(fromLatLng, toLatLng, null);
            case "BICYCLING" -> amapRouteTool.bicyclingRoute(fromLatLng, toLatLng, null);
            case "ELECTROBIKE" -> amapRouteTool.electrobikeRoute(fromLatLng, toLatLng, null);
            case "TRANSIT" -> amapRouteTool.transitRoute(fromLatLng, toLatLng, city, city, null, null, null);
            default -> amapRouteTool.drivingRoute(fromLatLng, toLatLng, null, null, null, null);
        };
    }

    /**
     * 从上下文获取城市名称。
     * 优先级：query.city &gt; cityProfile.destination &gt; requirement.destination
     *
     * @param query   查询项
     * @param context 工作流上下文
     * @return 城市名称，未找到返回null
     */
    private String getCityFromContext(QueryItem query, GenerateWorkflowContext context) {
        // 优先使用查询项中的城市
        if (query.getCity() != null && !query.getCity().isBlank()) {
            return query.getCity();
        }
        // 其次使用城市概况中的目的地
        if (context.getCityProfile() != null
                && context.getCityProfile().destination() != null
                && !context.getCityProfile().destination().isBlank()) {
            return context.getCityProfile().destination();
        }
        // 最后使用需求中的目的地
        if (context.getRequirement() != null
                && context.getRequirement().getDestination() != null
                && !context.getRequirement().getDestination().isBlank()) {
            return context.getRequirement().getDestination();
        }
        return null;
    }

    /**
     * 解析位置为经纬度字符串。
     * 支持三种方式：
     * 1. 直接传入经纬度格式（如 "116.40, 39.90"）
     * 2. 通过高德地理编码API转换
     * 3. 从候选POI中匹配
     *
     * @param location 位置名称或经纬度
     * @param city     城市名称
     * @param context  工作流上下文
     * @return 格式化后的经纬度字符串，解析失败返回null
     */
    private String resolveLatLng(String location, String city, GenerateWorkflowContext context) {
        // 位置为空时，尝试从上下文获取备选位置
        if (location == null || location.isBlank()) {
            location = getAlternativeLocation(context);
            if (location == null || location.isBlank()) {
                return null;
            }
        }

        // 已是经纬度格式，直接格式化返回
        if (location.matches("^-?\\d+\\.?\\d*,\\s*-?\\d+\\.?\\d*$")) {
            return formatLatLng(location);
        }

        // 使用高德地理编码API转换
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

        // 从候选POI中匹配
        String candidateLatLng = getLatLngFromCandidates(location, context);
        if (candidateLatLng != null) {
            return formatLatLng(candidateLatLng);
        }

        return null;
    }

    /**
     * 获取备选位置。
     * 当原始位置为空时，从上下文获取目的地作为备选。
     *
     * @param context 工作流上下文
     * @return 备选位置名称，未找到返回null
     */
    private String getAlternativeLocation(GenerateWorkflowContext context) {
        if (context.getCityProfile() != null && context.getCityProfile().destination() != null) {
            return context.getCityProfile().destination();
        }
        if (context.getRequirement() != null && context.getRequirement().getDestination() != null) {
            return context.getRequirement().getDestination();
        }
        return null;
    }

    /**
     * 从候选POI中查找位置对应的经纬度。
     * 按顺序匹配：酒店候选 &gt; 景点候选 &gt; 美食候选
     *
     * @param locationName 位置名称
     * @param context      工作流上下文
     * @return 经纬度字符串，未找到返回null
     */
    private String getLatLngFromCandidates(String locationName, GenerateWorkflowContext context) {
        if (context.getCityProfile() == null || locationName == null) {
            return null;
        }

        // 优先匹配酒店候选
        if (context.getCityProfile().hotelCandidates() != null) {
            for (PoiCandidate candidate : context.getCityProfile().hotelCandidates()) {
                if (candidate.getName() != null && candidate.getName().contains(locationName)) {
                    return candidate.getLocation();
                }
            }
        }

        // 其次匹配景点候选
        if (context.getCityProfile().scenicCandidates() != null) {
            for (PoiCandidate candidate : context.getCityProfile().scenicCandidates()) {
                if (candidate.getName() != null && candidate.getName().contains(locationName)) {
                    return candidate.getLocation();
                }
            }
        }

        // 最后匹配美食候选
        if (context.getCityProfile().foodCandidates() != null) {
            for (PoiCandidate candidate : context.getCityProfile().foodCandidates()) {
                if (candidate.getName() != null && candidate.getName().contains(locationName)) {
                    return candidate.getLocation();
                }
            }
        }

        return null;
    }

    /**
     * 格式化经纬度字符串。
     * 将经纬度保留6位小数，确保格式统一。
     *
     * @param latLng 原始经纬度字符串
     * @return 格式化后的经纬度字符串
     */
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

    /**
     * 判断高德API响应是否可用（适用于驾车、步行、骑行等非公交路线）。
     *
     * @param response 高德API响应
     * @return true表示响应可用，false表示不可用
     */
    private boolean isUsable(AmapApiResp<Route> response) {
        return response != null
                && response.isSuccess()
                && response.getData() != null
                && response.getData().getPaths() != null
                && !response.getData().getPaths().isEmpty();
    }

    /**
     * 判断公交换乘API响应是否可用。
     * 公交路线使用transits字段而不是paths字段。
     *
     * @param response 高德API响应
     * @return true表示响应可用，false表示不可用
     */
    private boolean isTransitUsable(AmapApiResp<Route> response) {
        return response != null
                && response.isSuccess()
                && response.getData() != null
                && response.getData().getTransits() != null
                && !response.getData().getTransits().isEmpty();
    }

    /**
     * 将高德Route响应转换为TransportRoute（适用于驾车、步行、骑行等）。
     * 提取路线的关键信息：起点、终点、交通方式、耗时、距离等。
     *
     * @param route 高德Route对象
     * @param mode  交通方式
     * @param query 查询项（用于降级生成模拟路线）
     * @return TransportRoute对象
     */
    private TransportRoute convertToTransportRoute(Route route, String mode, QueryItem query) {
        if (route.getPaths() == null || route.getPaths().isEmpty()) {
            return createSimulatedRoute(query);
        }

        // 获取第一条路线方案
        Path path = route.getPaths().getFirst();

        // 获取总耗时（优先使用 Path.duration，其次累加所有 Step.cost.duration）
        int totalDurationSeconds;
        if (path.getDuration() != null && !path.getDuration().isBlank()) {
            try {
                totalDurationSeconds = Integer.parseInt(path.getDuration());
            } catch (NumberFormatException e) {
                log.warn("解析 Path.duration 失败: {}", path.getDuration());
                totalDurationSeconds = calculateTotalDuration(path);
            }
        } else {
            totalDurationSeconds = calculateTotalDuration(path);
        }

        // 获取总距离
        String distance = path.getDistance();

        // 获取出租车费用
        String taxiCost = route.getTaxiCost();

        // 获取红绿灯数量（优先从 Path 首个 Step 的 cost.traffic_lights 获取，或累加所有 Step）
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
     * 将公交换乘Route响应转换为TransportRoute。
     * 公交路线包含公交、地铁、步行、打车等混合信息，使用transits字段。
     *
     * @param route 高德Route对象（包含transits字段）
     * @param query 查询项（用于降级生成模拟路线）
     * @return TransportRoute对象
     */
    private TransportRoute convertTransitToTransportRoute(Route route, QueryItem query) {
        if (route.getTransits() == null || route.getTransits().isEmpty()) {
            return createSimulatedRoute(query);
        }

        // 获取第一条公交换乘方案
        Transit transit = route.getTransits().getFirst();

        // 从Transit获取距离（优先使用Transit的distance）
        String distance = transit.getDistance();
        if (distance == null || distance.isBlank()) {
            distance = "0";
        }

        // 获取总耗时（优先使用 Transit.Cost 的 duration，其次使用 Route.Cost 的 duration）
        int totalDurationSeconds = 0;
        if (transit.getCost() != null && transit.getCost().getDuration() != null && !transit.getCost().getDuration().isBlank()) {
            try {
                totalDurationSeconds = Integer.parseInt(transit.getCost().getDuration());
            } catch (NumberFormatException e) {
                log.warn("解析Transit.Cost耗时失败: {}", transit.getCost().getDuration());
            }
        } else if (route.getCost() != null && route.getCost().getDuration() != null && !route.getCost().getDuration().isBlank()) {
            try {
                totalDurationSeconds = Integer.parseInt(route.getCost().getDuration());
            } catch (NumberFormatException e) {
                log.warn("解析Route.Cost耗时失败: {}", route.getCost().getDuration());
            }
        }

        // 获取费用信息
        String taxiFee = null;      // 预估出租车费用（route级别）
        String transitFee = null;   // 公交换乘总花费

        if (route.getCost() != null) {
            taxiFee = route.getCost().getTaxi_fee();
        }
        if (transit.getCost() != null) {
            transitFee = transit.getCost().getTransit_fee();
            if (taxiFee == null && transit.getCost().getTaxi_fee() != null) {
                taxiFee = transit.getCost().getTaxi_fee();
            }
        }
        if (transitFee == null && route.getCost() != null) {
            transitFee = route.getCost().getTransit_fee();
        }

        // 统计换乘次数（通过统计公交分段数计算）
        int transferCount = 0;
        if (transit.getSegments() != null) {
            // 统计包含公交或地铁的分段数作为换乘次数
            transferCount = (int) transit.getSegments().stream()
                    .filter(seg -> seg.getBus() != null)
                    .count();
            if (transferCount > 0) {
                transferCount--; // 减去第一段，得到换乘次数
            }
        }

        // 检查是否为夜班车
        boolean isNightBus = "1".equals(transit.getNightflag());

        // 记录详细日志
        log.info("公交路线计算结果 - 距离: {}米, 耗时: {}秒, 出租车费用: {}元, 公交费用: {}元, 换乘次数: {}次, 夜班车: {}",
                distance, totalDurationSeconds, taxiFee, transitFee, transferCount, isNightBus);

        // 输出分段详情
        logTransitSegments(transit);

        return new TransportRoute(
                formatLatLng(route.getOrigin()),
                formatLatLng(route.getDestination()),
                "TRANSIT",
                formatDuration(totalDurationSeconds),
                formatDistance(distance),
                "AMAP_API",
                false
        );
    }

    /**
     * 输出公交分段详情日志。
     * 包含步行、公交、出租车等各分段信息。
     *
     * @param transit 公交方案
     */
    private void logTransitSegments(Transit transit) {
        if (transit.getSegments() == null || transit.getSegments().isEmpty()) {
            return;
        }

        int segmentIndex = 1;
        for (TransitSegment segment : transit.getSegments()) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("分段[%d]: ", segmentIndex));

            // 步行信息
            if (segment.getWalking() != null) {
                WalkInfo walking = segment.getWalking();
                int walkDistance = 0;
                int walkDuration = 0;

                if (walking.getDistance() != null && !walking.getDistance().isBlank()) {
                    try {
                        walkDistance = Integer.parseInt(walking.getDistance());
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                }
                if (walking.getCost() != null && walking.getCost().getDuration() != null
                        && !walking.getCost().getDuration().isBlank()) {
                    try {
                        walkDuration = Integer.parseInt(walking.getCost().getDuration());
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                }

                sb.append(String.format("步行(%d米, %d秒) ", walkDistance, walkDuration));
            }

            // 公交信息
            if (segment.getBus() != null) {
                BusInfo bus = segment.getBus();
                double busDistance = getBusDistance(bus);
                int busLineCount = bus.getBuslines() != null ? bus.getBuslines().size() : 0;
                sb.append(String.format("公交(%.0f米, %d条线路) ", busDistance, busLineCount));
            }

            // 地铁信息
            if (segment.getRailway() != null) {
                RailwayInfo railway = segment.getRailway();
                sb.append(String.format("地铁[%s](%s米, %s秒) ",
                        railway.getName(),
                        railway.getDistance(),
                        railway.getTime()));
            }

            // 出租车信息
            if (segment.getTaxi() != null) {
                TaxiInfo taxi = segment.getTaxi();
                sb.append(String.format("打车(%s米, %s秒, %s元) ",
                        taxi.getDistance(),
                        taxi.getDrivetime(),
                        taxi.getPrice()));
            }

            log.debug(sb.toString().trim());
            segmentIndex++;
        }
    }

    private static double getBusDistance(BusInfo bus) {
        double busDistance = 0.0;

        if (bus.getBuslines() != null) {
            for (BusLine busLine : bus.getBuslines()) {
                if (busLine.getDistance() != null) {
                    busDistance += busLine.getDistance();
                }
            }
        }
        return busDistance;
    }

    /**
     * 从Path中计算总耗时。
     * 累加所有Step的cost.duration，单位为秒。
     *
     * @param path 路线方案
     * @return 总耗时（秒）
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
     * 计算红绿灯总数。
     * 累加所有Step的cost.traffic_lights。
     *
     * @param path 路线方案
     * @return 红绿灯总数
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
     * 例如：3660秒 -> "约1小时1分钟"，300秒 -> "约5分钟"
     *
     * @param totalSeconds 总秒数
     * @return 格式化后的时间文本
     */
    private String formatDuration(int totalSeconds) {
        if (totalSeconds <= 0) {
            return "未知时间";
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
     * 例如：1500米 -> "约1.5公里"，500米 -> "约500米"
     *
     * @param distanceMeters 距离（米）
     * @return 格式化后的距离文本
     */
    private String formatDistance(String distanceMeters) {
        if (distanceMeters == null) {
            return "未知公里数";
        }

        try {
            int meters = (int) Double.parseDouble(distanceMeters);
            if (meters >= 1000) {
                return String.format("约%.1f公里", meters / 1000.0);
            }
            return "约" + meters + "米";
        } catch (NumberFormatException e) {
            return "约0公里";
        }
    }

    /**
     * 创建模拟路线（降级方案）。
     * 当真实路线API调用失败或经纬度解析失败时使用。
     *
     * @param query 查询项
     * @return 模拟的交通路线
     */
    private TransportRoute createSimulatedRoute(QueryItem query) {
        return new TransportRoute(
                query.from(),
                query.to(),
                "TAXI",
                "约0分钟",
                "约0公里",
                "SIMULATED_AMAP",
                true
        );
    }

    /**
     * 根据天数查找已存在的DayDataPackage。
     * 用于检查是否已生成该天的路线数据。
     *
     * @param context 工作流上下文
     * @param day     天数
     * @return 对应的DayDataPackage，未找到返回null
     */
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