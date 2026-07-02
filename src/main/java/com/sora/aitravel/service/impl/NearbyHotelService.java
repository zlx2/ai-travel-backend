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
                TripPlanDTO.Spot lastSpot = findLastSpot(day);
                if (lastSpot == null) {
                    log.info("附近酒店搜索跳过，day={}，无景点", day.getDay());
                    continue;
                }
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

    private List<TripPlanDTO.NearbyHotel> searchNearby(String location, String city) {
        JSONObject response =
                amapApiService.searchPoiAroundRaw(
                        location,
                        null,
                        HOTEL_TYPE_CODE,
                        city,
                        false,
                        SEARCH_RADIUS_METERS,
                        "distance",
                        MAX_HOTELS,
                        1,
                        "business");
        return parseHotels(response);
    }

    private List<TripPlanDTO.NearbyHotel> parseHotels(JSONObject response) {
        List<TripPlanDTO.NearbyHotel> hotels = new ArrayList<>();
        if (response == null) {
            return hotels;
        }
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
            hotel.setName(poiJson.getStr("name"));
            hotel.setAddress(poiJson.getStr("address"));
            hotel.setSource("AMAP_AROUND");

            String locationStr = poiJson.getStr("location");
            if (locationStr != null && locationStr.contains(",")) {
                String[] parts = locationStr.split(",");
                try {
                    hotel.setLng(new BigDecimal(parts[0].trim()));
                    hotel.setLat(new BigDecimal(parts[1].trim()));
                    hotel.setCoordType("GCJ02");
                } catch (NumberFormatException ignored) {
                }
            }

            String distanceStr = poiJson.getStr("distance");
            if (distanceStr != null) {
                try {
                    hotel.setDistanceMeters(Integer.parseInt(distanceStr));
                } catch (NumberFormatException ignored) {
                }
            }

            JSONObject business = poiJson.getJSONObject("business");
            if (business != null) {
                hotel.setTel(business.getStr("tel"));
                hotel.setRating(business.getStr("rating"));
            }

            hotel.setEstimatedCost(estimateCost(hotel.getRating(), hotel.getName()));
            hotel.setEstimatedPrice(estimatePrice(hotel.getRating(), hotel.getName()));

            if (hotel.getName() != null && hotel.getLng() != null && hotel.getLat() != null) {
                hotels.add(hotel);
            }
        }
        return hotels;
    }

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

    private String resolveCity(TripPlanDTO.DailyPlan day) {
        if (day.getSpots() != null) {
            for (TripPlanDTO.Spot spot : day.getSpots()) {
                String city = spot.getCity();
                if (city != null && !city.isBlank() && city.length() <= 6) {
                    return city;
                }
            }
        }
        return day.getCity();
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
