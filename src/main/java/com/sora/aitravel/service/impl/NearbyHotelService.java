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

/** 在最后景点附近搜索酒店，返回地图标记数据。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NearbyHotelService {

    private static final String HOTEL_TYPE_CODE = "100100";
    private static final int SEARCH_RADIUS_METERS = 2000;
    private static final int MAX_HOTELS = 8;

    private final AmapApiService amapApiService;

    /**
     * 根据每天最后一个景点的坐标，搜索附近酒店。
     *
     * @param dailyPlans 已生成的每日行程列表
     */
    public void fillNearbyHotels(List<TripPlanDTO.DailyPlan> dailyPlans) {
        if (dailyPlans == null) {
            return;
        }
        for (TripPlanDTO.DailyPlan day : dailyPlans) {
            try {
                // 1.找到最后一天景区
                TripPlanDTO.Spot lastSpot = findLastSpot(day);
                if (lastSpot == null) {
                    log.info("附近酒店搜索跳过，day={}，无景点", day.getDay());
                    continue;
                }
                // 2.获得其坐标
                BigDecimal lng = firstNonNull(lastSpot.getEntranceLng(), lastSpot.getLng());
                BigDecimal lat = firstNonNull(lastSpot.getEntranceLat(), lastSpot.getLat());
                if (lng == null || lat == null) {
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
                String location = lng + "," + lat;
                String searchCity = resolveCity(day);
                log.info(
                        "附近酒店搜索开始，day={}，lastSpot={}，location={}，city={}",
                        day.getDay(),
                        lastSpot.getName(),
                        location,
                        searchCity);
                // 3。 调用搞都周边搜索酒店
                List<TripPlanDTO.NearbyHotel> hotels = searchNearby(location, searchCity);
                day.setNearbyHotels(hotels);
                log.info(
                        "附近酒店搜索完成，day={}, lastSpot={}, hotelCount={}",
                        day.getDay(),
                        lastSpot.getName(),
                        hotels.size());
            } catch (Exception exception) {
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

    /** 将附近酒店数据填充到 STAY_AREA 时间线节点上，供前端地图渲染标记和详情。 */
    public void enrichStayAreaNode(TripPlanDTO.DailyPlan dailyPlan) {
        List<TripPlanDTO.NearbyHotel> hotels = dailyPlan.getNearbyHotels();
        if (hotels == null || hotels.isEmpty() || dailyPlan.getTimeline() == null) {
            return;
        }
        dailyPlan.getTimeline().stream()
                .filter(node -> "STAY_AREA".equals(node.getType()))
                .findFirst()
                .ifPresent(
                        stayNode -> {
                            TripPlanDTO.NearbyHotel first = hotels.get(0);
                            stayNode.setTitle(first.getName());
                            stayNode.setLng(first.getLng());
                            stayNode.setLat(first.getLat());
                            stayNode.setCoordType(first.getCoordType());
                            stayNode.setAddress(first.getAddress());
                            stayNode.setNearbyHotels(hotels);
                            stayNode.setCompact(false);
                            if (first.getEstimatedCost() != null) {
                                stayNode.setEstimatedCost(first.getEstimatedCost());
                                stayNode.setCostText("约¥" + first.getEstimatedCost() + "/晚");
                            }
                            stayNode.setTags(
                                    List.of(
                                            "酒店",
                                            first.getDistanceMeters() != null
                                                    ? first.getDistanceMeters() + "m"
                                                    : "",
                                            first.getEstimatedPrice() != null
                                                    ? first.getEstimatedPrice()
                                                    : ""));
                        });
    }

    /** 根据评分和名称关键词估算酒店均价区间。 */
    private String estimatePrice(String rating, String name) {
        double score = parseRating(rating);
        boolean isLuxury = isLuxuryHotel(name);
        if (isLuxury || score >= 4.7) {
            return "¥500-900/晚";
        }
        if (score >= 4.5) {
            return "¥350-600/晚";
        }
        if (score >= 4.0) {
            return "¥250-450/晚";
        }
        if (score >= 3.5) {
            return "¥180-300/晚";
        }
        return "¥150-280/晚";
    }

    private double parseRating(String rating) {
        if (rating == null || rating.isBlank()) {
            return 0;
        }
        try {
            return Double.parseDouble(rating);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private boolean isLuxuryHotel(String name) {
        if (name == null) {
            return false;
        }
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
