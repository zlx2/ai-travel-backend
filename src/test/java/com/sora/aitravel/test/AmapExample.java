package com.sora.aitravel.test;

import com.sora.aitravel.dto.model.AmapApiResp;
import com.sora.aitravel.dto.model.geo.GeoCode;
import com.sora.aitravel.dto.model.geo.RegeoCode;
import com.sora.aitravel.dto.model.poi.Poi;
import com.sora.aitravel.dto.model.route.Route;
import com.sora.aitravel.service.AmapApiService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
public class AmapExample {
    // 初始化服务
    @Autowired
    AmapApiService amapService;

    @Test
    void test() {
        // 1. POI文本搜索
        System.out.println("=== POI文本搜索 ===");
        AmapApiResp<List<Poi>> poiResult = amapService.searchPoiText("北京大学", "141201", "北京市", true);
        if (poiResult.isSuccess()) {
            for (Poi poi : poiResult.getData()) {
                System.out.println(poi);
                System.out.printf("名称: %s, 地址: %s, 经纬度: %s%n",
                        poi.getName(), poi.getAddress(), poi.getLocation());
            }
        } else {
            System.out.println("搜索失败: " + poiResult.getInfo());
        }

        // 2. POI周边搜索
        System.out.println("\n=== POI周边搜索 ===");
        AmapApiResp<List<Poi>> aroundResult = amapService.searchPoiAround("116.473168,39.993015", 5000);
        if (aroundResult.isSuccess()) {
            for (Poi poi : aroundResult.getData()) {
                System.out.println(poi);
                System.out.printf("名称: %s, 地址: %s, 距离: %s米%n",
                        poi.getName(), poi.getAddress(), poi.getDistance());
            }
        }

        // 3. 地名转经纬度
        System.out.println("\n=== 地名转经纬度 ===");
        AmapApiResp<List<GeoCode>> geoResult = amapService.geoCode("北京市朝阳区阜通东大街6号", "北京");
        if (geoResult.isSuccess()) {
            for (GeoCode geo : geoResult.getData()) {
                System.out.println(geo);
                System.out.printf("地址: %s, 经纬度: %s, 匹配级别: %s%n",
                        geo.getFormattedAddress(), geo.getLocation(), geo.getLevel());
            }
        }

        // 4. 经纬度转地名
        System.out.println("\n=== 经纬度转地名 ===");
        AmapApiResp<RegeoCode> regeoResult = amapService.reGeoCode("116.481488,39.990464", 1000, "all");
        if (regeoResult.isSuccess()) {
            RegeoCode regeo = regeoResult.getData();
            System.out.println("格式化地址: " + regeo.getFormattedAddress());
            if (regeo.getPois() != null) {
                regeo.getPois().forEach(poi ->
                        System.out.printf(poi.toString()));
            }
        }
    }

    @Test
    void test02() {
        // 5. 驾车路径规划
        System.out.println("\n=== 驾车路径规划 ===");
        AmapApiResp<Route> drivingResult = amapService.drivingRoute("116.481028,39.989643", "116.434446,39.90816");
        if (drivingResult.isSuccess()) {
            System.out.println(drivingResult);
//            Route route = drivingResult.getData();
//            System.out.printf("起点: %s, 终点: %s, 预计打车费: %s元%n",
//                    route.getOrigin(), route.getDestination(), route.getTaxiCost());
//            if (route.getPaths() != null && !route.getPaths().isEmpty()) {
//                var path = route.getPaths().get(0);
//                System.out.println(path);
//                System.out.printf("方案距离: %s米, 预计时间: %s秒%n",
//                        path.getDistance(), path.getDuration());
//            }
        }
    }

    @Test
    void test03() {
        // 6. 公交路径规划
        System.out.println("\n=== 公交路径规划 ===");
        AmapApiResp<Route> transitResult = amapService.transitRoute(
                "116.481028,39.989643", "116.434446,39.90816",
                "010", "010", 0, 5, 0);
        if (transitResult.isSuccess()) {
            System.out.println(transitResult);
//            Route route = transitResult.getData();
//            System.out.printf("起点: %s, 终点: %s%n", route.getOrigin(), route.getDestination());
//            if (route.getTransits() != null) {
//                route.getTransits().forEach(transit ->
//                        System.out.printf(transit.toString()));
//            }
        }
    }
}
