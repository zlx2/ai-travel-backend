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

@Slf4j
@Service
@RequiredArgsConstructor
public class RegionRoutePresetServiceImpl {

    private static final String PRESETS_PATH = "travel/region-route-presets.json";

    private final JsonCodec jsonCodec;

    private Map<String, RegionPresetGroup> groups = Map.of();

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

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    @Data
    public static class RegionPresetGroup {
        private String type;
        private List<String> aliases;
        private String defaultCity;
        private List<RoutePreset> presets;
        private String standardName;
    }

    @Data
    public static class RoutePreset {
        private String name;
        private Integer minDays;
        private Integer maxDays;
        private List<String> cities;
        private List<String> tags;
    }
}
