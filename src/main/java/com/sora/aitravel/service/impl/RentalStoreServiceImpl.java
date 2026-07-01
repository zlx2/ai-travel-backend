package com.sora.aitravel.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.enums.RentalStoreUsageEnum;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.dto.model.RentalStoreDTO;
import com.sora.aitravel.service.AmapApiService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 基于高德 POI 的租车服务点解析实现。
 *
 * <p>核心流程：
 *
 * <ol>
 *   <li>先搜索用户输入的目标地点，拿到中心坐标；
 *   <li>再搜索中心点周边的租车 POI；
 *   <li>过滤出租车、非汽车租赁等无效点；
 *   <li>根据取车/还车用途、距离、标签、评分打分；
 *   <li>返回一个结构化推荐点，供后续库存、价格、订单模块继续使用。
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class RentalStoreServiceImpl {

    /** 当前只在目标地点 5 公里范围内找租车服务点，避免推荐过远地点。 */
    private static final int SEARCH_RADIUS_METERS = 5000;

    private final AmapApiService amapApiService;

    public RentalStoreDTO resolveRentalStore(
            String targetName, String cityName, RentalStoreUsageEnum usage) {
        JSONObject targetPoi = searchFirstPoi(targetName, cityName);
        String location = text(targetPoi, "location");
        if (location.isBlank()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "目标地点缺少坐标：" + targetName);
        }

        JSONObject aroundResult =
                amapApiService.searchPoiAroundRaw(
                        location,
                        "租车",
                        null,
                        cityName,
                        true,
                        SEARCH_RADIUS_METERS,
                        null,
                        10,
                        1,
                        "business,navi");
        checkAmapResult(aroundResult);
        JSONArray pois = aroundResult.getJSONArray("pois");
        if (pois == null || pois.isEmpty()) {
            return buildPlanGoServicePoint(targetPoi, targetName, usage);
        }

        JSONObject bestStore = selectBestRentalStore(pois, usage, targetPoi, targetName);
        return buildRentalStoreResponse(bestStore, targetName, usage);
    }

    /**
     * 从高德返回的候选 POI 中选出最合适的租车点。
     *
     * <p>该方法保持包内可见，方便单元测试直接验证筛选和打分规则。
     */
    JSONObject selectBestRentalStore(JSONArray pois, RentalStoreUsageEnum usage) {
        return selectBestRentalStore(pois, usage, null, null);
    }

    JSONObject selectBestRentalStore(
            JSONArray pois, RentalStoreUsageEnum usage, JSONObject targetPoi, String targetName) {
        List<JSONObject> validStores = new ArrayList<>();
        for (Object item : pois) {
            JSONObject poi = (JSONObject) item;
            if (isValidRentalStore(poi)) {
                validStores.add(poi);
            }
        }

        if (validStores.isEmpty()) {
            if (targetPoi != null) {
                return planGoServicePointPoi(targetPoi, targetName, usage);
            }
            throw new BusinessException(ErrorCode.NOT_FOUND, "目标地点附近没有可用租车点");
        }

        return validStores.stream()
                .max(Comparator.comparingInt(poi -> scoreRentalStore(poi, usage)))
                .orElseThrow();
    }

    /**
     * 判断 POI 是否可以作为租车服务点候选。
     *
     * <p>重点排除出租车相关点位；优先信任高德汽车租赁类型码，类型码不准时再用名称和业务标签兜底。
     */
    boolean isValidRentalStore(JSONObject poi) {
        String name = text(poi, "name");
        String typecode = text(poi, "typecode");
        String keytag = businessText(poi, "keytag");
        String rectag = businessText(poi, "rectag");

        if (name.contains("出租车")) {
            return false;
        }

        if (typecode.contains("151100")) {
            return false;
        }

        if (typecode.contains("010900") || typecode.contains("010901")) {
            return true;
        }

        return name.contains("租车") && (keytag.contains("汽车租赁") || rectag.contains("汽车租赁"));
    }

    /**
     * 给候选租车点打分。
     *
     * <p>取车优先“汽车租赁”主类型，返还优先“汽车维修/租赁相关服务”类型；随后叠加汽车租赁标签、名称命中、 距离和评分。该分值只用于候选排序，不对用户展示。
     */
    int scoreRentalStore(JSONObject poi, RentalStoreUsageEnum usage) {
        String name = text(poi, "name");
        String typecode = text(poi, "typecode");
        String keytag = businessText(poi, "keytag");

        int distance = intValue(text(poi, "distance"), 999999);
        double rating = doubleValue(businessText(poi, "rating"), 0.0);

        int score = 0;

        if (usage == RentalStoreUsageEnum.PICKUP) {
            if (typecode.contains("010900")) {
                score += 100;
            } else if (typecode.contains("010901")) {
                score += 80;
            }
        }

        if (usage == RentalStoreUsageEnum.RETURN) {
            if (typecode.contains("010901")) {
                score += 100;
            } else if (typecode.contains("010900")) {
                score += 80;
            }
        }

        if (keytag.contains("汽车租赁")) {
            score += 40;
        }

        if (name.contains("租车")) {
            score += 20;
        }

        if (distance < SEARCH_RADIUS_METERS) {
            score += Math.max(0, 50 - distance / 20);
        }

        score += (int) (rating * 5);

        return score;
    }

    /**
     * 将高德 POI 转换为系统内部可继续流转的结构化结果。
     *
     * <p>displayName 使用“目标地点 + 推荐取/还车点”，避免把第三方地图商户名称误表达为平台自营门店。
     */
    RentalStoreDTO buildRentalStoreResponse(
            JSONObject poi, String targetName, RentalStoreUsageEnum usage) {
        String poiId = text(poi, "id");
        String location = text(poi, "location");
        String[] lngLat = location.split(",");

        RentalStoreDTO.RentalStoreDTOBuilder builder =
                RentalStoreDTO.builder()
                        .storeCode("AMAP_" + poiId)
                        .displayName(
                                targetName
                                        + (usage == RentalStoreUsageEnum.PICKUP
                                                ? "推荐取车点"
                                                : "推荐还车点"))
                        .source("AMAP_DYNAMIC")
                        .usage(usage.name())
                        .amapPoiId(poiId)
                        .amapPoiName(text(poi, "name"))
                        .address(text(poi, "address"))
                        .cityName(text(poi, "cityname"))
                        .adName(text(poi, "adname"))
                        .adCode(text(poi, "adcode"))
                        .distanceMeters(intValue(text(poi, "distance"), 999999))
                        .typeCode(text(poi, "typecode"))
                        .openTime(businessText(poi, "opentime_today"))
                        .tel(businessText(poi, "tel"));

        if (lngLat.length == 2) {
            builder.lng(lngLat[0]).lat(lngLat[1]);
        }

        return builder.build();
    }

    private RentalStoreDTO buildPlanGoServicePoint(
            JSONObject targetPoi, String targetName, RentalStoreUsageEnum usage) {
        return buildRentalStoreResponse(
                planGoServicePointPoi(targetPoi, targetName, usage), targetName, usage);
    }

    private JSONObject planGoServicePointPoi(
            JSONObject targetPoi, String targetName, RentalStoreUsageEnum usage) {
        JSONObject poi = new JSONObject();
        poi.set("id", "PLANGO_" + Math.abs((targetName + usage.name()).hashCode()));
        poi.set("name", targetName + (usage == RentalStoreUsageEnum.PICKUP ? "送车服务点" : "还车服务点"));
        poi.set("location", text(targetPoi, "location"));
        poi.set("address", firstNonBlank(text(targetPoi, "address"), targetName));
        poi.set("cityname", text(targetPoi, "cityname"));
        poi.set("adname", text(targetPoi, "adname"));
        poi.set("adcode", text(targetPoi, "adcode"));
        poi.set("distance", "0");
        poi.set("typecode", "010900");
        JSONObject business = new JSONObject();
        business.set("keytag", "汽车租赁");
        business.set("rectag", "汽车租赁");
        business.set("rating", "4.8");
        business.set("opentime_today", "09:00-21:00");
        poi.set("business", business);
        return poi;
    }

    private String businessText(JSONObject poi, String fieldName) {
        JSONObject business = poi.getJSONObject("business");
        if (business == null || business.isEmpty()) {
            return "";
        }
        return text(business, fieldName);
    }

    private String text(JSONObject object, String fieldName) {
        if (object == null) {
            return "";
        }
        Object value = object.get(fieldName);
        if (value == null) {
            return "";
        }
        if (value instanceof JSONArray array && array.isEmpty()) {
            return "";
        }
        return String.valueOf(value);
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private JSONObject searchFirstPoi(String keywords, String cityName) {
        JSONObject result =
                amapApiService.searchPoiTextRaw(
                        keywords, null, cityName, true, 5, 1, "business,navi");
        checkAmapResult(result);
        JSONArray pois = result.getJSONArray("pois");
        if (pois == null || pois.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "没有找到目标地点：" + keywords);
        }
        for (Object item : pois) {
            JSONObject poi = (JSONObject) item;
            if (text(poi, "parent").isBlank()) {
                return poi;
            }
        }
        return pois.getJSONObject(0);
    }

    private void checkAmapResult(JSONObject result) {
        if (result == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "高德接口返回为空");
        }
        if (!"1".equals(text(result, "status"))) {
            throw new BusinessException(
                    ErrorCode.SYSTEM_ERROR,
                    "高德接口调用失败，info="
                            + text(result, "info")
                            + ", infocode="
                            + text(result, "infocode"));
        }
    }

    private int intValue(String value, int defaultValue) {
        try {
            if (value == null || value.isBlank()) {
                return defaultValue;
            }
            return Integer.parseInt(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private double doubleValue(String value, double defaultValue) {
        try {
            if (value == null || value.isBlank()) {
                return defaultValue;
            }
            return Double.parseDouble(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
