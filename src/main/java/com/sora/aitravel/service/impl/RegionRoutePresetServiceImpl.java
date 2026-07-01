package com.sora.aitravel.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sora.aitravel.service.RegionRoutePresetService;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RegionRoutePresetServiceImpl implements RegionRoutePresetService {

    private static final String PRESETS_PATH = "travel/region-route-presets.json";

    private Map<String, RegionPresetGroup> groups = Map.of();

    @PostConstruct
    void load() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ClassPathResource resource = new ClassPathResource(PRESETS_PATH);
            try (InputStream stream = resource.getInputStream()) {
                Map<String, RegionPresetGroup> loaded =
                        mapper.readValue(
                                stream, new TypeReference<Map<String, RegionPresetGroup>>() {});
                groups = Map.copyOf(loaded);
            }
            log.info("已加载 region-route-presets.json, keys={}", groups.keySet());
        } catch (Exception exception) {
            log.warn("加载 region-route-presets.json 失败，将使用空配置", exception);
            groups = Map.of();
        }
    }

    @Override
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

    @Override
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
}
