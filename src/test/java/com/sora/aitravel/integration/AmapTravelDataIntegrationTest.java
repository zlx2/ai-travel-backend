package com.sora.aitravel.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.sora.aitravel.config.AmapProperties;
import com.sora.aitravel.dto.model.poi.Poi;
import com.sora.aitravel.dto.model.route.Path;
import com.sora.aitravel.dto.model.route.Route;
import com.sora.aitravel.service.impl.AmapApiServiceImpl;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "AMAP_API_KEY", matches = ".+")
class AmapTravelDataIntegrationTest {

    private final AmapApiServiceImpl service = new AmapApiServiceImpl(properties());

    @Test
    void shouldParsePoiBusinessNaviAndPhotos() {
        List<Poi> pois =
                service.searchPoiText("成都夜市", "050000", "成都", true, 25, 1, "business,navi,photos")
                        .getData();

        assertThat(pois).isNotEmpty();
        Poi sample =
                pois.stream().filter(poi -> poi.getBusiness() != null).findFirst().orElseThrow();

        assertThat(sample.getId()).isNotBlank();
        assertThat(sample.getLocation()).contains(",");
        assertThat(sample.getBusiness().getOpentimeToday()).isNotBlank();
        assertThat(sample.getBusiness().getRating()).isNotBlank();
        assertThat(sample.getNavi()).isNotNull();
        assertThat(sample.getPhotos()).isNotNull();
    }

    @Test
    void shouldParseRouteDistanceDurationAndTaxiCost() {
        Route route =
                service.drivingRoute("104.065735,30.659462", "104.079043,30.574469").getData();

        assertThat(route).isNotNull();
        assertThat(route.getTaxiCost()).isNotBlank();
        assertThat(route.getPaths()).isNotEmpty();
        Path path = route.getPaths().get(0);
        assertThat(path.getDistance()).isNotBlank();
        assertThat(path.getCost()).isNotNull();
        assertThat(path.getCost().getDuration()).isNotBlank();
    }

    private AmapProperties properties() {
        AmapProperties properties = new AmapProperties();
        properties.setApiKey(System.getenv("AMAP_API_KEY"));
        properties.setBaseUrl("https://restapi.amap.com");
        properties.setTimeout(Duration.ofSeconds(20));
        return properties;
    }
}
