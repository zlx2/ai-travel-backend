package com.sora.aitravel.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.sora.aitravel.dto.model.TripPlanDTO;
import com.sora.aitravel.service.AmapApiService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 附近酒店搜索与数据增强服务。
 *
 * <p>核心职责：
 * <ul>
 *   <li>根据每天最后景点的坐标，调用高德周边搜索 API 获取附近酒店列表</li>
 *   <li>将酒店数据（名称、坐标、评分、价格估算）填充到 {@code DailyPlan.nearbyHotels}</li>
 *   <li>将推荐酒店信息填充到时间线的 {@code STAY_AREA} 节点，供前端地图渲染</li>
 * </ul>
 *
 * <p>数据源：高德地图 Web Service API（周边搜索），酒店类型编码 "100100"。
 * <p>坐标系：GCJ02（高德标准坐标系）。
 * <p>酒店数据不持久化到独立表，以 JSON 形式存储在行程结果中。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NearbyHotelService {

    /** 高德 POI 酒店类型编码，用于周边搜索时按类型过滤 */
    private static final String HOTEL_TYPE_CODE = "100100";
    /** 酒店搜索半径（米），以最后景点为圆心 */
    private static final int SEARCH_RADIUS_METERS = 2000;
    /** 每次搜索最多返回的酒店数量 */
    private static final int MAX_HOTELS = 8;

    /** 高德地图 API 服务，封装周边搜索、地理编码等能力 */
    private final AmapApiService amapApiService;

    /**
     * 根据每天最后一个景点的坐标，搜索附近酒店。
     *
     * <p>处理流程：
     * <ol>
     *   <li>遍历每日行程，找到当天 order 最大的景点（即最后游览的景点）</li>
     *   <li>以该景点坐标为圆心，调用高德周边搜索 API 获取附近酒店</li>
     *   <li>将搜索结果（含名称、坐标、评分、价格估算）挂到 {@code dailyPlan.nearbyHotels}</li>
     * </ol>
     *
     * <p>容错策略：单天搜索失败不影响其他天，仅记录 warn 日志继续执行。
     *
     * @param dailyPlans 已生成的每日行程列表，每个 DailyPlan 需包含 spots（景点列表）和 city（城市名）
     */
    public void fillNearbyHotels(List<TripPlanDTO.DailyPlan> dailyPlans) {
        // 空列表防御：传入 null 时直接返回，不抛异常
        if (dailyPlans == null) {
            return;
        }
        // 逐天处理，每天独立搜索，互不影响
        for (TripPlanDTO.DailyPlan day : dailyPlans) {
            try {
                // 1：找到当天 order 最大的景点（最后游览的景点）作为酒店搜索中心
                TripPlanDTO.Spot lastSpot = findLastSpot(day);
                if (lastSpot == null) {
                    // 当天无景点，跳过酒店搜索
                    log.info("附近酒店搜索跳过，day={}，无景点", day.getDay());
                    continue;
                }
                // 2：提取景点坐标，优先使用入口坐标（entranceLng/entranceLat），兜底用中心坐标
                BigDecimal lng = firstNonNull(lastSpot.getEntranceLng(), lastSpot.getLng());
                BigDecimal lat = firstNonNull(lastSpot.getEntranceLat(), lastSpot.getLat());
                if (lng == null || lat == null) {
                    // 景点无坐标信息，跳过
                    log.info(
                            "附近酒店搜索跳过，day={}，lastSpot={} 无坐标(entranceLng={}, lng={}, entranceLat={}, lat={})",
                            day.getDay(),
                            lastSpot.getName(),
                            lastSpot.getEntranceLng(),
                            lastSpot.getLng(),
                            lastSpot.getEntranceLat(),
                            lastSpot.getLat());
                    continue;
                }
                // 拼接高德 API 要求的 location 格式："经度,纬度"（如 "120.14,30.24"）
                String location = lng + "," + lat;
                // 从景点列表中提取城市名称，用于限定搜索范围
                String searchCity = resolveCity(day);
                log.info(
                        "附近酒店搜索开始，day={}，lastSpot={}，location={}，city={}",
                        day.getDay(),
                        lastSpot.getName(),
                        location,
                        searchCity);
                // 3：调用高德周边搜索 API，在景点附近 2000m 内搜索最多 8 家酒店
                List<TripPlanDTO.NearbyHotel> hotels = searchNearby(location, searchCity);
                // 将搜索到的酒店列表挂到当天行程的 nearbyHotels 字段
                day.setNearbyHotels(hotels);
                log.info(
                        "附近酒店搜索完成，day={}, lastSpot={}, hotelCount={}",
                        day.getDay(),
                        lastSpot.getName(),
                        hotels.size());
            } catch (Exception exception) {
                // 单天搜索失败不中断整体流程，仅记录警告日志
                log.warn(
                        "附近酒店搜索失败，day={}, reason={}",
                        day.getDay(),
                        exception.getMessage(),
                        exception);
            }
        }
    }

    /**
     * 调用高德周边搜索 API，在指定坐标附近搜索酒店。
     *
     * @param location 中心坐标，格式 "lng,lat"（如 "104.06,30.65"）
     * @param city     搜索城市名称
     * @return 附近酒店列表
     */
    private List<TripPlanDTO.NearbyHotel> searchNearby(String location, String city) {
        JSONObject response =
                amapApiService.searchPoiAroundRaw(
                        location,
                        null,              // 不限关键词，仅按类型筛选
                        HOTEL_TYPE_CODE,   // "100100" = 高德酒店类型编码
                        city,
                        false,             // cityLimit=false，不限城市边界
                        SEARCH_RADIUS_METERS, // 搜索半径（2000米）
                        "distance",        // 按距离从近到远排序
                        MAX_HOTELS,        // 最多返回 8 家
                        1,                 // 第 1 页
                        "business");       // 返回营业信息（评分、电话等）
        return parseHotels(response);
    }


    /**
     * 解析高德 API 返回的原始 JSON，转换为 NearbyHotel 对象列表。
     * 高德返回结构：{ "status": "1", "pois": [ { "name":..., "location":"lng,lat", ... } ] }
     */
    private List<TripPlanDTO.NearbyHotel> parseHotels(JSONObject response) {
        List<TripPlanDTO.NearbyHotel> hotels = new ArrayList<>();
        if (response == null) {
            return hotels;
        }
        // 高德 API 返回 status="1" 表示成功
        String status = response.getStr("status");
        if (!"1".equals(status)) {
            log.warn("高德附近酒店搜索失败，status={}, info={}", status, response.getStr("info"));
            return hotels;
        }
        JSONArray pois = response.getJSONArray("pois");
        if (pois == null || pois.isEmpty()) {
            return hotels;
        }
        for (Object poiObj : pois) {
            JSONObject poiJson = (JSONObject) poiObj;
            TripPlanDTO.NearbyHotel hotel = new TripPlanDTO.NearbyHotel();
            // 基础信息：名称、地址、数据来源标记
            hotel.setName(poiJson.getStr("name"));
            hotel.setAddress(poiJson.getStr("address"));
            hotel.setSource("AMAP_AROUND");

            // 坐标解析：高德返回 "lng,lat" 格式的字符串
            String locationStr = poiJson.getStr("location");
            if (locationStr != null && locationStr.contains(",")) {
                String[] parts = locationStr.split(",");
                try {
                    hotel.setLng(new BigDecimal(parts[0].trim()));
                    hotel.setLat(new BigDecimal(parts[1].trim()));
                    hotel.setCoordType("GCJ02");  // 高德使用 GCJ02 坐标系
                } catch (NumberFormatException ignored) {
                }
            }

            // 距离：高德返回字符串格式的米数
            String distanceStr = poiJson.getStr("distance");
            if (distanceStr != null) {
                try {
                    hotel.setDistanceMeters(Integer.parseInt(distanceStr));
                } catch (NumberFormatException ignored) {
                }
            }

            // 营业信息：从 business 子对象中提取电话和评分
            JSONObject business = poiJson.getJSONObject("business");
            if (business != null) {
                hotel.setTel(business.getStr("tel"));
                hotel.setRating(business.getStr("rating"));
            }

            // 价格估算：根据评分和名称关键词推算价格区间
            hotel.setEstimatedPrice(estimatePrice(hotel.getRating(), hotel.getName()));

            // 只保留名称和坐标都有效的酒店
            if (hotel.getName() != null && hotel.getLng() != null && hotel.getLat() != null) {
                hotels.add(hotel);
            }
        }
        return hotels;
    }

    /**
     * 找到当天行程中 order 最大的景点（即最后一个景点）。
     * 酒店搜索以该景点为中心坐标。
     */
    private TripPlanDTO.Spot findLastSpot(TripPlanDTO.DailyPlan day) {
        if (day.getSpots() == null || day.getSpots().isEmpty()) {
            return null;
        }
        return day.getSpots().stream()
                .filter(s -> s.getOrder() != null)
                .max((a, b) -> Integer.compare(a.getOrder(), b.getOrder()))
                .orElse(day.getSpots().get(day.getSpots().size() - 1));
    }

    private BigDecimal firstNonNull(BigDecimal first, BigDecimal second) {
        return first != null ? first : second;
    }

    /**
     * 从景点列表中提取城市名称（优先取第一个有效 city）。
     * 用于限定酒店搜索的城市范围。
     */
    private String resolveCity(TripPlanDTO.DailyPlan day) {
        if (day.getSpots() != null) {
            for (TripPlanDTO.Spot spot : day.getSpots()) {
                String city = spot.getCity();
                // 取长度 ≤6 的有效城市名（排除异常长字符串）
                if (city != null && !city.isBlank() && city.length() <= 6) {
                    return city;
                }
            }
        }
        return day.getCity();  // 兜底使用 dailyPlan 级别的 city
    }

    /** 根据评分和名称关键词估算酒店具体参考金额（元/晚）。 */
    private int estimateCost(String rating, String name) {
        double score = parseRating(rating);
        boolean isLuxury = isLuxuryHotel(name);
        if (isLuxury || score >= 4.7) {
            return 550;
        }
        if (score >= 4.5) {
            return 475;
        }
        if (score >= 4.0) {
            return 350;
        }
        if (score >= 3.5) {
            return 240;
        }
        return 215;
    }

    /**
     * 将附近酒店数据填充到 STAY_AREA 时间线节点上，供前端地图渲染标记和详情。
     *
     * <p>处理逻辑：
     * <ol>
     *   <li>在 timeline 中查找第一个 {@code type="STAY_AREA"} 的节点</li>
     *   <li>取 {@code nearbyHotels} 列表中的第一家酒店作为推荐酒店</li>
     *   <li>将酒店名称设为节点标题，坐标赋给节点位置，价格生成展示文案</li>
     *   <li>将整个酒店列表挂到节点的 {@code nearbyHotels} 字段，供前端按需消费</li>
     * </ol>
     *
     * <p>调用时机：在 {@code fillNearbyHotels} 之后、持久化之前调用，确保 timeline 节点携带完整酒店数据。
     *
     * @param dailyPlan 已填充 nearbyHotels 的单日行程计划
     */
    public void enrichStayAreaNode(TripPlanDTO.DailyPlan dailyPlan) {
        // 取出当天搜索到的酒店列表
        List<TripPlanDTO.NearbyHotel> hotels = dailyPlan.getNearbyHotels();
        // 前置校验：酒店列表为空或 timeline 不存在时直接返回，不做任何修改
        if (hotels == null || hotels.isEmpty() || dailyPlan.getTimeline() == null) {
            return;
        }
        // 在 timeline 中流式查找第一个 STAY_AREA 类型的节点
        dailyPlan.getTimeline().stream()
                .filter(node -> "STAY_AREA".equals(node.getType()))
                .findFirst()
                .ifPresent(
                        stayNode -> {
                            // 取列表中的第一家酒店作为推荐酒店（距离最近/评分最高）
                            TripPlanDTO.NearbyHotel first = hotels.get(0);
                            // 将推荐酒店名称设为 STAY_AREA 节点标题（替代默认的"住宿区域"）
                            stayNode.setTitle(first.getName());
                            // 将酒店坐标赋给节点，前端地图据此渲染标记位置
                            stayNode.setLng(first.getLng());
                            stayNode.setLat(first.getLat());
                            stayNode.setCoordType(first.getCoordType());
                            // 设置酒店地址，供前端详情弹窗展示
                            stayNode.setAddress(first.getAddress());
                            // 将整个酒店列表挂到节点上，前端可按需展示全部或仅第一家
                            stayNode.setNearbyHotels(hotels);
                            // 关闭紧凑展示模式，让前端展开酒店详情块
                            stayNode.setCompact(false);
                            // 如果有估算费用，设置费用展示文案（如"约¥350/晚"）
                            if (first.getEstimatedCost() != null) {
                                stayNode.setEstimatedCost(first.getEstimatedCost());
                                stayNode.setCostText("约¥" + first.getEstimatedCost() + "/晚");
                            }
                            // 构建标签列表：固定包含"酒店"，附加距离和价格区间
                            stayNode.setTags(
                                    List.of(
                                            "酒店",
                                            // 距离标签：如 "800m"，无数据时为空字符串
                                            first.getDistanceMeters() != null
                                                    ? first.getDistanceMeters() + "m"
                                                    : "",
                                            // 价格区间标签：如 "¥250-450/晚"，无数据时为空字符串
                                            first.getEstimatedPrice() != null
                                                    ? first.getEstimatedPrice()
                                                    : ""));
                        });
    }

    /**
     * 根据评分和名称关键词估算酒店均价区间（展示用文案）。
     *
     * <p>与 {@link #estimateCost} 使用相同的分级策略，但返回价格区间描述而非具体金额。
     *
     * @param rating 酒店评分字符串
     * @param name   酒店名称
     * @return 价格区间描述，如 "¥250-450/晚"
     */
    private String estimatePrice(String rating, String name) {
        double score = parseRating(rating);        // 解析评分为数值
        boolean isLuxury = isLuxuryHotel(name);    // 判断是否豪华品牌
        if (isLuxury || score >= 4.7) {
            return "¥500-900/晚";                  // 豪华/高评分 → 高档价位区间
        }
        if (score >= 4.5) {
            return "¥350-600/晚";                  // 中高评分 → 中高档价位区间
        }
        if (score >= 4.0) {
            return "¥250-450/晚";                  // 中等评分 → 中档价位区间
        }
        if (score >= 3.5) {
            return "¥180-300/晚";                  // 较低评分 → 经济型价位区间
        }
        return "¥150-280/晚";                      // 无评分/低评分 → 最低价位区间
    }

    /**
     * 将评分字符串安全地解析为 double 数值。
     * 解析失败或输入为空时返回 0，不抛异常。
     *
     * @param rating 评分字符串，如 "4.5"
     * @return 评分数值，无法解析时返回 0
     */
    private double parseRating(String rating) {
        // 空值或空白字符串直接返回 0
        if (rating == null || rating.isBlank()) {
            return 0;
        }
        try {
            return Double.parseDouble(rating);     // 正常解析
        } catch (NumberFormatException ignored) {
            return 0;                              // 解析失败兜底为 0
        }
    }

    /**
     * 根据酒店名称判断是否为豪华酒店。
     *
     * <p>匹配关键词：度假、精品、国际、大酒店、威斯汀、希尔顿、万豪、香格里拉。
     * 命中任意一个关键词即判定为豪华酒店。
     *
     * @param name 酒店名称
     * @return true 表示豪华酒店
     */
    private boolean isLuxuryHotel(String name) {
        if (name == null) {
            return false;
        }
        // 名称中包含任一豪华关键词即判定为豪华酒店
        return name.contains("度假")
                || name.contains("精品")
                || name.contains("国际")
                || name.contains("大酒店")
                || name.contains("威斯汀")
                || name.contains("希尔顿")
                || name.contains("万豪")
                || name.contains("香格里拉");
    }
}
