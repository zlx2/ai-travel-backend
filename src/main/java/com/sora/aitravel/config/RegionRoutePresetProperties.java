package com.sora.aitravel.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RegionRoutePresetProperties {

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

    public static class RegionPresetGroup {
        private String type;
        private List<String> aliases;
        private String defaultCity;
        private List<RoutePreset> presets;
        private String standardName;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public List<String> getAliases() {
            return aliases;
        }

        public void setAliases(List<String> aliases) {
            this.aliases = aliases;
        }

        public String getDefaultCity() {
            return defaultCity;
        }

        public void setDefaultCity(String defaultCity) {
            this.defaultCity = defaultCity;
        }

        public List<RoutePreset> getPresets() {
            return presets;
        }

        public void setPresets(List<RoutePreset> presets) {
            this.presets = presets;
        }

        public String getStandardName() {
            return standardName;
        }

        public void setStandardName(String standardName) {
            this.standardName = standardName;
        }
    }

    public static class RoutePreset {
        private String name;
        private Integer minDays;
        private Integer maxDays;
        private List<String> cities;
        private List<String> tags;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getMinDays() {
            return minDays;
        }

        public void setMinDays(Integer minDays) {
            this.minDays = minDays;
        }

        public Integer getMaxDays() {
            return maxDays;
        }

        public void setMaxDays(Integer maxDays) {
            this.maxDays = maxDays;
        }

        public List<String> getCities() {
            return cities;
        }

        public void setCities(List<String> cities) {
            this.cities = cities;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }
    }
}
