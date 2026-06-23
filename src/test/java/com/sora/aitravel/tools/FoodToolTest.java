package com.sora.aitravel.tools;

import com.sora.aitravel.dto.model.FoodRestaurantItemDTO;
import com.sora.aitravel.dto.response.FoodRecommendResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 美食推荐工具手动测试类。
 *
 * <p>这个测试类不是为了看 FoodTool 能不能真实调用高德并返回数据。
 * 运行前需要本机已经配置 AMAP_API_KEY。
 */
@SpringBootTest
class FoodToolTest {

    @Autowired
    private FoodTool foodTool;

    /**
     * 测试地点附近搜索。
     *
     * <p>例子：用户想查“洪崖洞附近火锅”。这个场景会先把“洪崖洞”转成坐标，再查周边饭店。
     */
    @Test
    void testNearAddressFood() {
        FoodRecommendResponse response =
                foodTool.recommendFood("洪崖洞附近火锅", "", 1500, 10, 1);

        printResponse("地点附近搜索：洪崖洞附近火锅", response);
    }

    /**
     * 测试城市关键词搜索。
     *
     * <p>例子：用户想查“重庆火锅推荐”。这个场景不需要当前位置，直接按城市和关键词查饭店。
     */
    @Test
    void testCityKeywordFood() {
        FoodRecommendResponse response =
                foodTool.recommendFood("重庆火锅推荐", "", 1500, 10, 1);

        printResponse("城市关键词搜索：重庆火锅推荐", response);
    }

    /**
     * 测试当前位置附近搜索。
     *
     * <p>例子：用户想查“附近有什么好吃的”。这个场景必须传 currentLocation，也就是当前位置经纬度。
     */
    @Test
    void testNearCurrentFood() {
        FoodRecommendResponse response =
                foodTool.recommendFood("附近有什么好吃的", "106.57719,29.55657", 1500, 10, 1);

        printResponse("当前位置附近搜索：附近有什么好吃的", response);
    }

    /**
     * 测试失败场景。
     *
     * <p>用户想查“附近”，但是没有传当前位置。正常结果应该是 success=false，并提示需要定位。
     */
    @Test
    void testNearCurrentFoodWithoutLocation() {
        FoodRecommendResponse response =
                foodTool.recommendFood("附近有什么好吃的", "", 1500, 10, 1);

        printResponse("失败场景：附近搜索但不传定位", response);
    }

    /**
     * 打印完整返回结果。
     *
     * <p>先打印 FoodRecommendResponse 的整体字段，再打印 list 中每一家饭店的所有字段。
     */
    private void printResponse(String title, FoodRecommendResponse response) {
        System.out.println();
        System.out.println("========== " + title + " ==========");
        System.out.println("【整体返回信息】");
        System.out.println("success = " + response.getSuccess());
        System.out.println("message = " + response.getMessage());
        System.out.println("source = " + response.getSource());
        System.out.println("intentType = " + response.getIntentType());
        System.out.println("queryType = " + response.getQueryType());
        System.out.println("centerLocation = " + response.getCenterLocation());
        System.out.println("total = " + response.getTotal());

        if (response.getList() == null || response.getList().isEmpty()) {
            System.out.println();
            System.out.println("【饭店列表】为空");
            return;
        }

        System.out.println();
        System.out.println("【饭店列表】");
        for (int i = 0; i < response.getList().size(); i++) {
            FoodRestaurantItemDTO item = response.getList().get(i);

            System.out.println();
            System.out.println("----- 第 " + (i + 1) + " 家饭店 -----");
            System.out.println("amapPoiId = " + item.getAmapPoiId());
            System.out.println("name = " + item.getName());
            System.out.println("address = " + item.getAddress());
            System.out.println("cityName = " + item.getCityName());
            System.out.println("adName = " + item.getAdName());
            System.out.println("adCode = " + item.getAdCode());
            System.out.println("type = " + item.getType());
            System.out.println("typeCode = " + item.getTypeCode());
            System.out.println("foodType = " + item.getFoodType());
            System.out.println("location = " + item.getLocation());
            System.out.println("longitude = " + item.getLongitude());
            System.out.println("latitude = " + item.getLatitude());
            System.out.println("distance = " + item.getDistance());
            System.out.println("distanceText = " + item.getDistanceText());
            System.out.println("rating = " + item.getRating());
            System.out.println("avgCost = " + item.getAvgCost());
            System.out.println("tag = " + item.getTag());
            System.out.println("keyTag = " + item.getKeyTag());
            System.out.println("recTag = " + item.getRecTag());
            System.out.println("openTime = " + item.getOpenTime());
            System.out.println("businessArea = " + item.getBusinessArea());
            System.out.println("tel = " + item.getTel());
            System.out.println("aiRecommendReason = " + item.getAiRecommendReason());
        }
    }
}
