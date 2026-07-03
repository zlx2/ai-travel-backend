package com.sora.aitravel.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sora.aitravel.dto.model.AmapApiResp;
import com.sora.aitravel.dto.model.poi.Poi;
import com.sora.aitravel.service.AmapApiService;
import com.sora.aitravel.service.AmapPoiCacheService;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

/**
 * 高德POI缓存服务实现
 * 缓存高德原始POI查询结果，避免工作流重复请求相同城市数据
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AmapPoiCacheServiceImpl implements AmapPoiCacheService {

    private static final String KEY_PREFIX = "amap:poi:v1:";
    private static final Duration EMPTY_TTL = Duration.ofMinutes(10);
    private static final TypeReference<List<Poi>> POI_LIST_TYPE = new TypeReference<>() {};

    private final AmapApiService amapApiService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.amap.poi-cache-enabled:false}")
    private boolean poiCacheEnabled;

    /**
     * 搜索POI文本（带缓存）
     * 如果缓存启用且命中，则返回缓存结果；否则调用API查询并缓存
     *
     * @param keywords   搜索关键词
     * @param types      POI类型
     * @param region     区域
     * @param cityLimit  是否限制在城市内
     * @param pageSize   每页大小
     * @param pageNum    页码
     * @param showFields 显示字段
     * @param category   分类
     * @return POI列表
     */
    @Override
    public List<Poi> searchText(
            String keywords,
            String types,
            String region,
            boolean cityLimit,
            int pageSize,
            int pageNum,
            String showFields,
            String category) {
        if (!poiCacheEnabled) {
            return searchDirect(keywords, types, region, cityLimit, pageSize, pageNum, showFields);
        }
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

        List<Poi> pois =
                searchDirect(keywords, types, region, cityLimit, pageSize, pageNum, showFields);
        write(key, pois, pois.isEmpty() ? EMPTY_TTL : ttl(category));
        return pois;
    }

    /**
     * 直接搜索POI（不使用缓存）
     *
     * @param keywords   搜索关键词
     * @param types      POI类型
     * @param region     区域
     * @param cityLimit  是否限制在城市内
     * @param pageSize   每页大小
     * @param pageNum    页码
     * @param showFields 显示字段
     * @return POI列表
     */
    private List<Poi> searchDirect(
            String keywords,
            String types,
            String region,
            boolean cityLimit,
            int pageSize,
            int pageNum,
            String showFields) {
        AmapApiResp<List<Poi>> response =
                amapApiService.searchPoiText(
                        keywords, types, region, cityLimit, pageSize, pageNum, showFields);
        if (response == null || !response.isSuccess() || response.getData() == null) {
            return List.of();
        }
        return response.getData();
    }

    /**
     * 从缓存读取POI数据
     *
     * @param key 缓存键
     * @return POI列表，如果缓存不存在或损坏则返回null
     */
    private List<Poi> read(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, POI_LIST_TYPE);
        } catch (RuntimeException exception) {
            log.warn("读取高德 POI 缓存失败，降级为直查，key={}, reason={}", key, exception.getMessage());
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

    /**
     * 写入POI数据到缓存
     *
     * @param key  缓存键
     * @param pois POI列表
     * @param ttl  过期时间
     */
    private void write(String key, List<Poi> pois, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(pois), ttl);
        } catch (Exception exception) {
            log.warn("写入高德 POI 缓存失败，本次结果继续使用，key={}, reason={}", key, exception.getMessage());
        }
    }

    /**
     * 获取缓存过期时间
     * 美食和夜景类数据更新较频繁，设置较短的TTL
     *
     * @param category 分类
     * @return 过期时间
     */
    private Duration ttl(String category) {
        if ("FOOD".equals(category) || "NIGHT".equals(category)) {
            return Duration.ofHours(6);
        }
        return Duration.ofHours(24);
    }

    /**
     * 生成缓存键
     *
     * @param keywords   搜索关键词
     * @param types      POI类型
     * @param region     区域
     * @param cityLimit  是否限制在城市内
     * @param pageSize   每页大小
     * @param pageNum    页码
     * @param showFields 显示字段
     * @param category   分类
     * @return 缓存键
     */
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
        String hash = DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8));
        return KEY_PREFIX
                + normalize(region)
                + ":"
                + normalize(category)
                + ":"
                + pageNum
                + ":"
                + hash;
    }

    /**
     * 规范化字符串，替换非法字符
     *
     * @param value 原始字符串
     * @return 规范化后的字符串
     */
    private String normalize(String value) {
        return text(value).replaceAll("[^\\p{L}\\p{N}_-]", "_");
    }

    /**
     * 安全获取文本，空值返回空字符串
     *
     * @param value 原始值
     * @return 处理后的文本
     */
    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}