package com.sora.aitravel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sora.aitravel.dto.model.AmapApiResp;
import com.sora.aitravel.dto.model.poi.Poi;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

/** 缓存高德原始 POI 查询结果，避免工作流重复请求相同城市数据。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AmapPoiCacheService {

    private static final String KEY_PREFIX = "amap:poi:v1:";
    private static final Duration EMPTY_TTL = Duration.ofMinutes(10);
    private static final TypeReference<List<Poi>> POI_LIST_TYPE = new TypeReference<>() {};

    private final AmapApiService amapApiService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public List<Poi> searchText(
            String keywords,
            String types,
            String region,
            boolean cityLimit,
            int pageSize,
            int pageNum,
            String showFields,
            String category) {
        String key =
                cacheKey(
                        keywords,
                        types,
                        region,
                        cityLimit,
                        pageSize,
                        pageNum,
                        showFields,
                        category);
        List<Poi> cached = read(key);
        if (cached != null) {
            log.info(
                    "高德 POI 缓存命中，category={}, region={}, keywords={}, page={}, count={}",
                    category,
                    region,
                    keywords,
                    pageNum,
                    cached.size());
            return cached;
        }

        AmapApiResp<List<Poi>> response =
                amapApiService.searchPoiText(
                        keywords,
                        types,
                        region,
                        cityLimit,
                        pageSize,
                        pageNum,
                        showFields);
        if (response == null || !response.isSuccess() || response.getData() == null) {
            return List.of();
        }
        List<Poi> pois = response.getData();
        write(key, pois, pois.isEmpty() ? EMPTY_TTL : ttl(category));
        return pois;
    }

    private List<Poi> read(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, POI_LIST_TYPE);
        } catch (RuntimeException exception) {
            log.warn(
                    "读取高德 POI 缓存失败，降级为直查，key={}, reason={}",
                    key,
                    exception.getMessage());
            return null;
        } catch (Exception exception) {
            log.warn("高德 POI 缓存数据损坏，忽略缓存，key={}", key, exception);
            try {
                redisTemplate.delete(key);
            } catch (RuntimeException ignored) {
                log.debug("删除损坏缓存失败，key={}", key);
            }
            return null;
        }
    }

    private void write(String key, List<Poi> pois, Duration ttl) {
        try {
            redisTemplate
                    .opsForValue()
                    .set(key, objectMapper.writeValueAsString(pois), ttl);
        } catch (Exception exception) {
            log.warn(
                    "写入高德 POI 缓存失败，本次结果继续使用，key={}, reason={}",
                    key,
                    exception.getMessage());
        }
    }

    private Duration ttl(String category) {
        if ("FOOD".equals(category) || "NIGHT".equals(category)) {
            return Duration.ofHours(6);
        }
        return Duration.ofHours(24);
    }

    private String cacheKey(
            String keywords,
            String types,
            String region,
            boolean cityLimit,
            int pageSize,
            int pageNum,
            String showFields,
            String category) {
        String raw =
                String.join(
                        "|",
                        text(region),
                        text(category),
                        text(keywords),
                        text(types),
                        String.valueOf(cityLimit),
                        String.valueOf(pageSize),
                        String.valueOf(pageNum),
                        text(showFields));
        String hash =
                DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8));
        return KEY_PREFIX + normalize(region) + ":" + normalize(category) + ":" + pageNum + ":" + hash;
    }

    private String normalize(String value) {
        return text(value).replaceAll("[^\\p{L}\\p{N}_-]", "_");
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
