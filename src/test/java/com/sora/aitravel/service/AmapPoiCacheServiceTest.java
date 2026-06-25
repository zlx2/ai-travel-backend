package com.sora.aitravel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sora.aitravel.dto.model.AmapApiResp;
import com.sora.aitravel.dto.model.poi.Poi;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class AmapPoiCacheServiceTest {

    @Mock private AmapApiService amapApiService;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private AmapPoiCacheService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new AmapPoiCacheService(amapApiService, redisTemplate, new ObjectMapper());
    }

    @Test
    void cacheMissShouldQueryAmapAndCacheScenicPoiForOneDay() {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(amapApiService.searchPoiText(
                        "成都公园", null, "成都", true, 25, 1, "business,navi,photos"))
                .thenReturn(success(List.of(poi("交子公园"))));

        List<Poi> result =
                service.searchText(
                        "成都公园",
                        null,
                        "成都",
                        true,
                        25,
                        1,
                        "business,navi,photos",
                        "SCENIC");

        assertThat(result).extracting(Poi::getName).containsExactly("交子公园");
        verify(valueOperations)
                .set(anyString(), anyString(), eq(Duration.ofHours(24)));
    }

    @Test
    void cacheHitShouldNotQueryAmap() throws Exception {
        String cachedJson = new ObjectMapper().writeValueAsString(List.of(poi("锦里古街")));
        when(valueOperations.get(anyString())).thenReturn(cachedJson);

        List<Poi> result =
                service.searchText(
                        "成都古街",
                        null,
                        "成都",
                        true,
                        25,
                        1,
                        "business,navi,photos",
                        "SCENIC");

        assertThat(result).extracting(Poi::getName).containsExactly("锦里古街");
        verify(amapApiService, times(0))
                .searchPoiText(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void redisFailureShouldFallBackToAmap() {
        when(valueOperations.get(anyString())).thenThrow(new IllegalStateException("redis down"));
        when(amapApiService.searchPoiText(
                        "成都夜市", null, "成都", true, 25, 1, "business,navi,photos"))
                .thenReturn(success(List.of(poi("夜猫子夜市"))));

        List<Poi> result =
                service.searchText(
                        "成都夜市",
                        null,
                        "成都",
                        true,
                        25,
                        1,
                        "business,navi,photos",
                        "NIGHT");

        assertThat(result).extracting(Poi::getName).containsExactly("夜猫子夜市");
    }

    private AmapApiResp<List<Poi>> success(List<Poi> pois) {
        AmapApiResp<List<Poi>> response = new AmapApiResp<>();
        response.setStatus("1");
        response.setData(pois);
        return response;
    }

    private Poi poi(String name) {
        Poi poi = new Poi();
        poi.setId("poi-" + name);
        poi.setName(name);
        poi.setLocation("104.0,30.0");
        return poi;
    }
}
