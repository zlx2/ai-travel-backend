package com.sora.aitravel.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sora.aitravel.common.utils.JsonCodec;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 区域路线预设服务实现
 * 加载和管理区域路线预设配置，用于匹配目的地和选择合适的路线预设
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegionRoutePresetServiceImpl {

    private static final String PRESETS_PATH = "travel/region-route-presets.json";

    private final JsonCodec jsonCodec;

    private Map<String, RegionPresetGroup> groups = Map.of();

    /**
     * 初始化加载预设配置
     * 从classpath加载region-route-presets.json文件
     */
    @PostConstruct
    void load() {
        try {
            Map<String, RegionPresetGroup> loaded =
                    jsonCodec.readClasspath(
                            PRESETS_PATH,
                            new TypeReference<Map<String, RegionPresetGroup>>() {},
                            "加载 region-route-presets.json 失败");
            groups = Map.copyOf(loaded);
            log.info("已加载 region-route-presets.json, keys={}", groups.keySet());
        } catch (Exception exception) {
            log.warn("加载 region-route-presets.json 失败，将使用空配置", exception);
            groups = Map.of();
        }
    }

    /**
     * 根据目的地匹配预设组
     * 先精确匹配，再尝试别名匹配
     *
     * @param destination 目的地名称
     * @return 匹配的预设组，如果未找到则返回null
     */
    public RegionPresetGroup matchGroup(String destination) {
        if (destination == null) {
            return null;
        }
        RegionPresetGroup group = groups.get(destination);
        if (group != null) {
            return group;
        }
        for (Map.Entry<String, RegionPresetGroup> entry : groups.entrySet()) {
            String standardName = entry.getKey();
            RegionPresetGroup value = entry.getValue();
            if (value.getAliases() != null
                    && value.getAliases().stream().anyMatch(alias -> alias.equals(destination))) {
                group = new RegionPresetGroup();
                group.setType(value.getType());
                group.setAliases(value.getAliases());
                group.setDefaultCity(value.getDefaultCity());
                group.setPresets(value.getPresets());
                group.setStandardName(standardName);
                return group;
            }
        }
        return null;
    }

    /**
     * 根据天数和偏好选择最合适的路线预设
     * 使用评分算法：天数匹配得10分，偏好标签匹配每个得3分，天数不匹配则扣分
     *
     * @param group       区域预设组
     * @param days        旅行天数
     * @param preferences 用户偏好列表
     * @return 最佳匹配的路线预设，如果未找到则返回null
     */
    public RoutePreset selectPreset(RegionPresetGroup group, int days, List<String> preferences) {
        if (group == null || group.getPresets() == null) {
            return null;
        }
        List<RoutePreset> presets = group.getPresets();
        RoutePreset bestPreset = null;
        int bestScore = -1;
        for (RoutePreset preset : presets) {
            int score = 0;
            if (preset.getMinDays() != null
                    && preset.getMaxDays() != null
                    && days >= preset.getMinDays()
                    && days <= preset.getMaxDays()) {
                score += 10;
            } else {
                int dist =
                        Math.min(
                                Math.abs(days - nullToZero(preset.getMinDays())),
                                Math.abs(days - nullToZero(preset.getMaxDays())));
                score -= dist * 2;
            }
            if (preferences != null && preset.getTags() != null) {
                for (String tag : preset.getTags()) {
                    if (preferences.stream().anyMatch(p -> p.contains(tag))) {
                        score += 3;
                    }
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestPreset = preset;
            }
        }
        return bestPreset;
    }

    /**
     * 将null转换为0
     *
     * @param value 整数值
     * @return 如果为null返回0，否则返回原值
     */
    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * 区域预设组
     * 包含某一区域的多个路线预设
     */
    @Data
    public static class RegionPresetGroup {
        private String type;
        private List<String> aliases;
        private String defaultCity;
        private List<RoutePreset> presets;
        private String standardName;
    }

    /**
     * 路线预设
     * 定义一条预设路线的配置
     */
    @Data
    public static class RoutePreset {
        private String name;
        private Integer minDays;
        private Integer maxDays;
        private List<String> cities;
        private List<String> tags;
    }
}