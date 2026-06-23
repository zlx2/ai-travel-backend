package com.sora.aitravel.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.sora.aitravel.common.enums.FoodSearchIntentTypeEnum;
import com.sora.aitravel.dto.model.FoodRestaurantItemDTO;
import com.sora.aitravel.dto.response.FoodRecommendResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 美食推荐工具测试。
 *
 * <p>这些测试会真实调用 FoodTool，并通过高德 API 查询饭店数据。运行前需要本机已配置 AMAP_API_KEY。
 */
@SpringBootTest
class FoodToolTest {

    @Autowired
    private FoodTool foodTool;

    /**
     * 测试“某个地点附近的美食”场景。
     *
     * <p>用户输入“洪崖洞附近火锅”时，后端应该先把“洪崖洞”转成经纬度，再调用高德周边搜索查饭店。
     */
    @Test
    void shouldRecommendFoodNearAddress() {
        FoodRecommendResponse response =
                foodTool.recommendFood("洪崖洞附近火锅", "", 1500, 10, 1);

        printResponse("洪崖洞附近火锅", response);

        assertSuccessResponse(response);
        assertThat(response.getIntentType()).isEqualTo(FoodSearchIntentTypeEnum.NEAR_ADDRESS);
        assertThat(response.getQueryType()).isEqualTo("AROUND");
        assertThat(response.getCenterLocation()).isNotBlank();
    }

    /**
     * 测试“城市 + 美食关键词”场景。
     *
     * <p>用户输入“重庆火锅推荐”时，后端应该识别出城市是“重庆”，关键词是“火锅”，然后调用高德关键字搜索。
     */
    @Test
    void shouldRecommendFoodByCityKeyword() {
        FoodRecommendResponse response =
                foodTool.recommendFood("重庆火锅推荐", "", 1500, 10, 1);

        printResponse("重庆火锅推荐", response);

        assertSuccessResponse(response);
        assertThat(response.getIntentType()).isEqualTo(FoodSearchIntentTypeEnum.CITY_KEYWORD);
        assertThat(response.getQueryType()).isEqualTo("TEXT");
    }

    /**
     * 测试“当前位置附近美食”场景。
     *
     * <p>用户输入“附近有什么好吃的”并传入当前位置坐标时，后端应该直接以该坐标为中心查周边饭店。
     */
    @Test
    void shouldRecommendFoodNearCurrentLocation() {
        FoodRecommendResponse response =
                foodTool.recommendFood("附近有什么好吃的", "106.57719,29.55657", 1500, 10, 1);

        printResponse("附近有什么好吃的", response);

        assertSuccessResponse(response);
        assertThat(response.getIntentType()).isEqualTo(FoodSearchIntentTypeEnum.NEAR_CURRENT);
        assertThat(response.getQueryType()).isEqualTo("AROUND");
        assertThat(response.getCenterLocation()).isEqualTo("106.57719,29.55657");
    }

    /**
     * 测试异常场景：用户想查“附近”，但是没有传当前位置。
     *
     * <p>这种情况下不能调用高德周边搜索，因为没有中心点坐标，应该返回 success=false 和明确提示。
     */
    @Test
    void shouldReturnFailWhenNearCurrentWithoutLocation() {
        FoodRecommendResponse response =
                foodTool.recommendFood("附近有什么好吃的", "", 1500, 10, 1);

        printResponse("附近不传定位", response);

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getMessage()).containsAnyOf("当前位置", "定位");
        assertThat(response.getTotal()).isZero();
        assertThat(response.getList()).isEmpty();
    }

    /**
     * 公共成功断言。
     *
     * <p>三个成功测试都会检查这些基础字段：是否成功、数据来源、列表数量、第一家饭店的核心字段。
     */
    private void assertSuccessResponse(FoodRecommendResponse response) {
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getSource()).isEqualTo("AMAP");
        assertThat(response.getTotal()).isPositive();
        assertThat(response.getList()).isNotEmpty();

        FoodRestaurantItemDTO first = response.getList().getFirst();
        assertThat(first.getName()).isNotBlank();
        assertThat(first.getAddress()).isNotBlank();
        assertThat(first.getLocation()).isNotBlank();
        assertThat(first.getLongitude()).isNotNull();
        assertThat(first.getLatitude()).isNotNull();
        assertThat(first.getAiRecommendReason()).isNotBlank();
    }

    /**
     * 打印测试结果。
     *
     * <p>单元测试本身主要靠断言判断是否通过；这里额外打印完整饭店列表，是为了方便人工查看高德实际返回了哪些数据。
     */
    private void printResponse(String title, FoodRecommendResponse response) {
        System.out.println("========== " + title + " ==========");
        System.out.println("success=" + response.getSuccess());
        System.out.println("message=" + response.getMessage());
        System.out.println("source=" + response.getSource());
        System.out.println("intentType=" + response.getIntentType());
        System.out.println("queryType=" + response.getQueryType());
        System.out.println("centerLocation=" + response.getCenterLocation());
        System.out.println("total=" + response.getTotal());
        if (response.getList() == null || response.getList().isEmpty()) {
            System.out.println("list 为空");
            return;
        }

        System.out.println("========== 饭店列表 ==========");
        for (int i = 0; i < response.getList().size(); i++) {
            FoodRestaurantItemDTO item = response.getList().get(i);

            System.out.println((i + 1) + ". " + item.getName());
            System.out.println("   地址：" + item.getAddress());
            System.out.println("   坐标：" + item.getLocation());
            System.out.println("   距离：" + item.getDistanceText());
            System.out.println("   类型：" + item.getFoodType());
            System.out.println("   评分：" + item.getRating());
            System.out.println("   人均：" + item.getAvgCost());
            System.out.println("   电话：" + item.getTel());
            System.out.println("   推荐理由：" + item.getAiRecommendReason());
            System.out.println();
        }
    }
}
