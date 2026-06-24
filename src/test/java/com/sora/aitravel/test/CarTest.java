package com.sora.aitravel.test;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.Data;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.WebClient;

/** 租车业务相关的功能测试 */
@SpringBootTest
public class CarTest {

    @Value("${app.amap.api-key}")
    private String amapKey;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final WebClient webClient =
            WebClient.builder().baseUrl("https://restapi.amap.com").build();

    /** 第一步：通过关键字搜索，查“杭州东站”的 POI。 */
    @Test
    void testSearchTextPoi() {
        JsonNode result =
                webClient
                        .get()
                        .uri(
                                uriBuilder ->
                                        uriBuilder
                                                .path("/v5/place/text")
                                                .queryParam("key", amapKey)
                                                .queryParam("keywords", "杭州东站")
                                                .queryParam("region", "杭州市")
                                                .queryParam("city_limit", "true")
                                                .queryParam("page_size", 5)
                                                .queryParam("show_fields", "business,navi")
                                                .build())
                        .retrieve()
                        .bodyToMono(JsonNode.class)
                        .block();

        assertNotNull(result);

        System.out.println("完整返回：");
        System.out.println(result.toPrettyString());

        assertEquals("1", result.get("status").asText(), "高德接口调用失败：" + result.get("info").asText());

        JsonNode pois = result.get("pois");
        assertNotNull(pois);
        assertTrue(pois.isArray());
        assertTrue(pois.size() > 0, "没有查到 POI");

        JsonNode firstPoi = pois.get(0);

        System.out.println("第一个 POI：");
        System.out.println("name = " + firstPoi.get("name").asText());
        System.out.println("id = " + firstPoi.get("id").asText());
        System.out.println("location = " + firstPoi.get("location").asText());
        System.out.println("address = " + firstPoi.get("address").asText());
        System.out.println("cityname = " + firstPoi.get("cityname").asText());
        System.out.println("adname = " + firstPoi.get("adname").asText());
    }

    /** 第二步：先查“杭州东站”坐标，再查周边租车 POI。 */
    @Test
    void testSearchRentalAroundHangzhouEastStation() {
        JsonNode targetPoi = searchFirstPoi("杭州东站", "杭州市");

        String location = targetPoi.get("location").asText();

        System.out.println("目标地点：" + targetPoi.get("name").asText());
        System.out.println("目标坐标：" + location);

        JsonNode result =
                webClient
                        .get()
                        .uri(
                                uriBuilder ->
                                        uriBuilder
                                                .path("/v5/place/around")
                                                .queryParam("key", amapKey)
                                                .queryParam("location", location)
                                                .queryParam("radius", 5000)
                                                .queryParam("keywords", "租车")
                                                .queryParam("region", "杭州市")
                                                .queryParam("city_limit", "true")
                                                .queryParam("page_size", 10)
                                                .queryParam("show_fields", "business,navi")
                                                .build())
                        .retrieve()
                        .bodyToMono(JsonNode.class)
                        .block();

        assertNotNull(result);

        System.out.println("周边租车 POI 返回：");
        System.out.println(result.toPrettyString());

        assertEquals("1", result.get("status").asText(), "高德接口调用失败：" + result.get("info").asText());

        JsonNode pois = result.get("pois");
        assertNotNull(pois);
        assertTrue(pois.isArray());

        System.out.println("查到租车相关 POI 数量：" + pois.size());

        for (JsonNode poi : pois) {
            System.out.println("---------------");
            System.out.println("name = " + text(poi, "name"));
            System.out.println("id = " + text(poi, "id"));
            System.out.println("location = " + text(poi, "location"));
            System.out.println("distance = " + text(poi, "distance") + " 米");
            System.out.println("type = " + text(poi, "type"));
            System.out.println("typecode = " + text(poi, "typecode"));
            System.out.println("address = " + text(poi, "address"));
        }
    }

    private JsonNode searchFirstPoi(String keywords, String city) {
        JsonNode result =
                webClient
                        .get()
                        .uri(
                                uriBuilder ->
                                        uriBuilder
                                                .path("/v5/place/text")
                                                .queryParam("key", amapKey)
                                                .queryParam("keywords", keywords)
                                                .queryParam("region", city)
                                                .queryParam("city_limit", "true")
                                                .queryParam("page_size", 5)
                                                .queryParam("show_fields", "business,navi")
                                                .build())
                        .retrieve()
                        .bodyToMono(JsonNode.class)
                        .block();

        assertNotNull(result);
        assertEquals("1", result.get("status").asText(), "高德接口调用失败：" + result.get("info").asText());

        JsonNode pois = result.get("pois");
        assertNotNull(pois);
        assertTrue(pois.isArray());
        assertTrue(pois.size() > 0, "没有查到目标地点：" + keywords);

        return pois.get(0);
    }

    @Test
    void testSelectBestRentalStoreAroundHangzhouEastStation() throws JsonProcessingException {
        String location = "120.212605,30.290846";

        JsonNode result =
                webClient
                        .get()
                        .uri(
                                uriBuilder ->
                                        uriBuilder
                                                .path("/v5/place/around")
                                                .queryParam("key", amapKey)
                                                .queryParam("location", location)
                                                .queryParam("radius", 5000)
                                                .queryParam("keywords", "租车")
                                                .queryParam("region", "杭州市")
                                                .queryParam("city_limit", "true")
                                                .queryParam("page_size", 10)
                                                .queryParam("show_fields", "business,navi")
                                                .build())
                        .retrieve()
                        .bodyToMono(JsonNode.class)
                        .block();

        assertNotNull(result);
        assertEquals("1", result.get("status").asText(), "高德接口调用失败：" + result.get("info").asText());

        JsonNode pois = result.get("pois");
        assertNotNull(pois);
        assertTrue(pois.isArray());

        List<JsonNode> validStores = new ArrayList<>();

        for (JsonNode poi : pois) {
            if (isValidRentalStore(poi)) {
                validStores.add(poi);
            }
        }

        System.out.println("有效租车候选点数量：" + validStores.size());

        for (JsonNode poi : validStores) {
            System.out.println("---------------");
            System.out.println("name = " + text(poi, "name"));
            System.out.println("id = " + text(poi, "id"));
            System.out.println("distance = " + text(poi, "distance"));
            System.out.println("typecode = " + text(poi, "typecode"));
            System.out.println("keytag = " + businessText(poi, "keytag"));
            System.out.println("rating = " + businessText(poi, "rating"));
            System.out.println("address = " + text(poi, "address"));
            System.out.println("score = " + scoreRentalStore(poi, StoreUsage.PICKUP));
        }

        JsonNode bestPickupStore =
                validStores.stream()
                        .max(
                                Comparator.comparingInt(
                                        poi -> scoreRentalStore(poi, StoreUsage.PICKUP)))
                        .orElseThrow(() -> new RuntimeException("没有可用取车点"));

        System.out.println("======== 推荐取车点 ========");
        System.out.println("name = " + text(bestPickupStore, "name"));
        System.out.println("id = " + text(bestPickupStore, "id"));
        System.out.println("location = " + text(bestPickupStore, "location"));
        System.out.println("address = " + text(bestPickupStore, "address"));
        System.out.println("distance = " + text(bestPickupStore, "distance"));
        System.out.println("typecode = " + text(bestPickupStore, "typecode"));
        VirtualRentalStoreDTO virtualStore =
                buildVirtualStore(bestPickupStore, "杭州东站", StoreUsage.PICKUP);

        System.out.println("======== 虚拟门店 DTO ========");
        System.out.println(
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(virtualStore));
    }

    enum StoreUsage {
        PICKUP,
        RETURN
    }

    private boolean isValidRentalStore(JsonNode poi) {
        String name = text(poi, "name");
        String typecode = text(poi, "typecode");
        String keytag = businessText(poi, "keytag");
        String rectag = businessText(poi, "rectag");

        // 1. 明确排除出租车
        if (name.contains("出租车")) {
            return false;
        }

        if (typecode.contains("151100")) {
            return false;
        }

        // 2. 明确汽车租赁类型
        if (typecode.contains("010900") || typecode.contains("010901")) {
            return true;
        }

        // 3. 兜底：高德分类不准，但业务标签和名字都像租车
        return name.contains("租车") && (keytag.contains("汽车租赁") || rectag.contains("汽车租赁"));
    }

    private int scoreRentalStore(JsonNode poi, StoreUsage usage) {
        String name = text(poi, "name");
        String typecode = text(poi, "typecode");
        String keytag = businessText(poi, "keytag");

        int distance = intValue(text(poi, "distance"), 999999);
        double rating = doubleValue(businessText(poi, "rating"), 0.0);

        int score = 0;

        // 1. 类型分
        if (usage == StoreUsage.PICKUP) {
            if (typecode.contains("010900")) {
                score += 100;
            } else if (typecode.contains("010901")) {
                score += 80;
            }
        }

        if (usage == StoreUsage.RETURN) {
            if (typecode.contains("010901")) {
                score += 100;
            } else if (typecode.contains("010900")) {
                score += 80;
            }
        }

        // 2. 标签分
        if (keytag.contains("汽车租赁")) {
            score += 40;
        }

        // 3. 名字分
        if (name.contains("租车")) {
            score += 20;
        }

        // 4. 距离分：越近越好，最多加 50 分
        if (distance < 5000) {
            score += Math.max(0, 50 - distance / 20);
        }

        // 5. 评分分
        score += (int) (rating * 5);

        return score;
    }

    private String businessText(JsonNode poi, String fieldName) {
        JsonNode business = poi.get("business");
        if (business == null || business.isNull()) {
            return "";
        }

        JsonNode value = business.get(fieldName);
        if (value == null || value.isNull()) {
            return "";
        }

        return value.asText();
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

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return "";
        }
        return value.asText();
    }

    @Data
    static class VirtualRentalStoreDTO {
        private String storeCode;
        private String displayName;
        private String source;
        private String usage;
        private String amapPoiId;
        private String amapPoiName;
        private String address;
        private String cityName;
        private String adName;
        private String adCode;
        private String lng;
        private String lat;
        private Integer distanceMeters;
        private String typeCode;
        private String openTime;
        private String tel;
    }

    private VirtualRentalStoreDTO buildVirtualStore(
            JsonNode poi, String targetName, StoreUsage usage) {
        VirtualRentalStoreDTO dto = new VirtualRentalStoreDTO();

        String poiId = text(poi, "id");
        String poiName = text(poi, "name");
        String location = text(poi, "location");

        String[] lngLat = location.split(",");

        dto.setStoreCode("AMAP_" + poiId);

        // 前台展示名不要直接叫“一嗨租车”，避免变成“冒充直营网点”
        if (usage == StoreUsage.PICKUP) {
            dto.setDisplayName(targetName + "推荐取车点");
        } else {
            dto.setDisplayName(targetName + "推荐还车点");
        }

        dto.setSource("AMAP_DYNAMIC");
        dto.setUsage(usage.name());

        dto.setAmapPoiId(poiId);
        dto.setAmapPoiName(poiName);

        dto.setAddress(text(poi, "address"));
        dto.setCityName(text(poi, "cityname"));
        dto.setAdName(text(poi, "adname"));
        dto.setAdCode(text(poi, "adcode"));

        if (lngLat.length == 2) {
            dto.setLng(lngLat[0]);
            dto.setLat(lngLat[1]);
        }

        dto.setDistanceMeters(intValue(text(poi, "distance"), 999999));
        dto.setTypeCode(text(poi, "typecode"));
        dto.setOpenTime(businessText(poi, "opentime_today"));
        dto.setTel(businessText(poi, "tel"));

        return dto;
    }
}
