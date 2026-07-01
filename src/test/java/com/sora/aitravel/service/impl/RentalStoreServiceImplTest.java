package com.sora.aitravel.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.sora.aitravel.common.enums.RentalStoreUsageEnum;
import com.sora.aitravel.dto.model.RentalStoreDTO;
import org.junit.jupiter.api.Test;

class RentalStoreServiceImplTest {

    private final RentalStoreServiceImpl service = new RentalStoreServiceImpl(null);

    @Test
    void shouldExcludeTaxiPoi() {
        JSONObject taxi = poi("杭州东站出租车服务点", "151100", "汽车租赁", 300, 4.8);

        assertThat(service.isValidRentalStore(taxi)).isFalse();
    }

    @Test
    void shouldSelectPickupStoreByTypeDistanceAndRating() {
        JSONArray pois = new JSONArray();
        pois.add(poi("普通汽车服务", "010900", "汽车租赁", 900, 3.5));
        pois.add(poi("一嗨租车杭州东站店", "010900", "汽车租赁", 300, 4.8));
        pois.add(poi("杭州东站出租车", "151100", "", 100, 5.0));

        JSONObject selected = service.selectBestRentalStore(pois, RentalStoreUsageEnum.PICKUP);

        assertThat(selected.getStr("name")).isEqualTo("一嗨租车杭州东站店");
    }

    @Test
    void shouldBuildRentalStoreDto() {
        JSONObject poi = poi("一嗨租车杭州东站店", "010900", "汽车租赁", 392, 4.8);

        RentalStoreDTO response =
                service.buildRentalStoreResponse(poi, "杭州东站", RentalStoreUsageEnum.PICKUP);

        assertThat(response.getStoreCode()).isEqualTo("AMAP_B0TEST");
        assertThat(response.getDisplayName()).isEqualTo("杭州东站推荐取车点");
        assertThat(response.getUsage()).isEqualTo("PICKUP");
        assertThat(response.getLng()).isEqualTo("120.211303");
        assertThat(response.getLat()).isEqualTo("30.287505");
        assertThat(response.getDistanceMeters()).isEqualTo(392);
    }

    @Test
    void shouldBuildPlanGoServicePointWhenAmapRentalPoisAreInvalid() {
        JSONArray pois = new JSONArray();
        pois.add(poi("成都东站出租车服务点", "151100", "汽车租赁", 120, 4.8));
        JSONObject target = poi("成都东站", "150200", "", 0, 0);

        JSONObject selected =
                service.selectBestRentalStore(pois, RentalStoreUsageEnum.PICKUP, target, "成都东站");
        RentalStoreDTO response =
                service.buildRentalStoreResponse(selected, "成都东站", RentalStoreUsageEnum.PICKUP);

        assertThat(response.getStoreCode()).startsWith("AMAP_PLANGO_");
        assertThat(response.getDisplayName()).isEqualTo("成都东站推荐取车点");
        assertThat(response.getAmapPoiName()).isEqualTo("成都东站送车服务点");
        assertThat(response.getDistanceMeters()).isZero();
    }

    private JSONObject poi(
            String name, String typeCode, String keytag, int distance, double rating) {
        JSONObject poi =
                JSONUtil.createObj()
                        .set("id", "B0TEST")
                        .set("name", name)
                        .set("location", "120.211303,30.287505")
                        .set("address", "杭州东站西广场 P6 停车场")
                        .set("cityname", "杭州市")
                        .set("adname", "上城区")
                        .set("adcode", "330102")
                        .set("distance", String.valueOf(distance))
                        .set("typecode", typeCode);

        poi.set(
                "business",
                JSONUtil.createObj()
                        .set("keytag", keytag)
                        .set("rectag", keytag)
                        .set("rating", String.valueOf(rating))
                        .set("opentime_today", "08:00-23:00")
                        .set("tel", "0571-00000000"));
        return poi;
    }
}
